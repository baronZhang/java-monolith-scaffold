package com.example.monolith.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.monolith.auth.domain.AuthSession;
import com.example.monolith.auth.domain.AuthSessionRepository;
import com.example.monolith.auth.domain.RefreshToken;
import com.example.monolith.auth.domain.RefreshTokenRepository;
import com.example.monolith.auth.domain.RefreshTokenStatus;
import com.example.monolith.auth.domain.RevokeReason;
import com.example.monolith.auth.domain.SessionStatus;
import com.example.monolith.shared.error.BusinessException;
import com.example.monolith.shared.error.ErrorCode;
import com.example.monolith.support.MutableClock;
import com.example.monolith.user.UserAccount;
import com.example.monolith.user.UserApi;
import com.example.monolith.user.UserProfile;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 认证用例的单元测试：所有端口都换成内存实现（Fake），不启动 Spring、不连数据库。
 * 这正是依赖倒置的回报——应用层只依赖接口，测试时想换什么就换什么。
 * 并发场景（两个请求同时刷新）依赖数据库行锁，放在集成测试 RefreshTokenConcurrencyIT 里。
 */
class AuthServiceTest {

    static final String PASSWORD = "secret-123";

    final MutableClock clock = new MutableClock(Instant.parse("2026-10-27T08:00:00Z"));
    // 测试里用最低强度的 BCrypt：默认强度 10 每次要几十毫秒，测试用不着那么安全
    final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    final FakeUsers users = new FakeUsers();
    final FakeSessions sessions = new FakeSessions();
    final FakeTokens tokens = new FakeTokens();
    final List<String> revokedSessions = new ArrayList<>();
    AuthService service;

    @BeforeEach
    void setUp() {
        users.add(new UserAccount(2, "editor", encoder.encode(PASSWORD), true, List.of("user:list")));
        TokenRevocationStore revocations = new TokenRevocationStore() {
            @Override
            public void revokeAccessToken(String tokenId, Instant until) {}

            @Override
            public void revokeSession(String sessionId, Instant until) {
                revokedSessions.add(sessionId);
            }

            @Override
            public void revokeUserTokensIssuedBefore(long userId, Instant cutoff, Instant until) {}

            @Override
            public boolean isRevoked(String tokenId, String sessionId, long userId, Instant issuedAt) {
                return false;
            }
        };
        AccessTokenIssuer issuer = request -> new AccessTokenIssuer.IssuedAccessToken(
                "at-" + request.sessionId() + "-" + request.issuedAt(), "jti", request.expiresAt());
        AuthProperties properties =
                new AuthProperties(Duration.ofMinutes(5), Duration.ofDays(7), "x".repeat(32), "test", "caffeine");
        service = new AuthService(
                users, encoder, sessions, tokens, revocations, issuer, properties, clock, new SimpleMeterRegistry());
    }

    @Test
    void 用户名或密码错误返回10001_不区分是哪一个错了() {
        assertThatThrownBy(() -> service.login("editor", "wrong"))
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.LOGIN_FAILED);
        assertThatThrownBy(() -> service.login("nobody", PASSWORD))
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.LOGIN_FAILED);
    }

    @Test
    void refresh_token用一次就作废_换出新的一对() {
        TokenPair first = service.login("editor", PASSWORD);

        TokenPair second = service.refresh(first.refreshToken());

        assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
        assertThat(tokens.statusOf(first.refreshToken())).isEqualTo(RefreshTokenStatus.USED);
        assertThat(tokens.statusOf(second.refreshToken())).isEqualTo(RefreshTokenStatus.ACTIVE);
    }

    @Test
    void 用过的refresh_token再次出现_判定为重放_整个会话作废() {
        TokenPair first = service.login("editor", PASSWORD);
        TokenPair second = service.refresh(first.refreshToken());

        // 攻击者拿着偷来的旧 token 来刷新
        assertThatThrownBy(() -> service.refresh(first.refreshToken()))
                .isInstanceOf(RefreshTokenRejectedException.class);

        // 会话被作废：连合法用户手里那个新 token 也不能用了，双方都得重新登录——被盗这件事因此暴露出来
        assertThat(sessions.only().status()).isEqualTo(SessionStatus.REVOKED);
        assertThat(sessions.only().revokeReason()).isEqualTo(RevokeReason.REUSE_DETECTED);
        assertThat(revokedSessions).containsExactly(sessions.only().id());
        assertThatThrownBy(() -> service.refresh(second.refreshToken()))
                .isInstanceOf(RefreshTokenRejectedException.class);
    }

    @Test
    void 过期的refresh_token不能用() {
        TokenPair pair = service.login("editor", PASSWORD);

        clock.advance(Duration.ofDays(7));

        assertThatThrownBy(() -> service.refresh(pair.refreshToken()))
                .isInstanceOf(RefreshTokenRejectedException.class);
    }

    @Test
    void 用户被禁用后_刷新失败且会话作废() {
        TokenPair pair = service.login("editor", PASSWORD);
        users.add(new UserAccount(2, "editor", encoder.encode(PASSWORD), false, List.of("user:list")));

        assertThatThrownBy(() -> service.refresh(pair.refreshToken()))
                .isInstanceOf(RefreshTokenRejectedException.class);
        assertThat(sessions.only().revokeReason()).isEqualTo(RevokeReason.USER_DISABLED);
        assertThatThrownBy(() -> service.login("editor", PASSWORD)).hasMessage("账号已被禁用，请联系管理员");
    }

    @Test
    void 退出登录后refresh_token也不能用了() {
        TokenPair pair = service.login("editor", PASSWORD);

        service.logout(sessions.only().id(), "jti", clock.instant().plusSeconds(300));

        assertThat(sessions.only().revokeReason()).isEqualTo(RevokeReason.LOGOUT);
        assertThatThrownBy(() -> service.refresh(pair.refreshToken()))
                .isInstanceOf(RefreshTokenRejectedException.class);
    }

    // ---------- 端口的内存实现 ----------

    static class FakeUsers implements UserApi {
        private final Map<Long, UserAccount> accounts = new HashMap<>();

        void add(UserAccount account) {
            accounts.put(account.userId(), account);
        }

        @Override
        public Optional<UserAccount> findAccountByUsername(String username) {
            return accounts.values().stream()
                    .filter(a -> a.username().equals(username))
                    .findFirst();
        }

        @Override
        public Optional<UserAccount> findAccount(long userId) {
            return Optional.ofNullable(accounts.get(userId));
        }

        @Override
        public Optional<UserProfile> findProfile(long userId) {
            return Optional.empty();
        }
    }

    static class FakeSessions implements AuthSessionRepository {
        private final Map<String, AuthSession> sessions = new HashMap<>();

        AuthSession only() {
            assertThat(sessions).hasSize(1);
            return sessions.values().iterator().next();
        }

        @Override
        public void insert(AuthSession session) {
            sessions.put(session.id(), session);
        }

        @Override
        public Optional<AuthSession> lockById(String id) {
            return Optional.ofNullable(sessions.get(id));
        }

        @Override
        public void updateStatus(AuthSession session, Instant updatedAt) {
            sessions.put(session.id(), session);
        }

        @Override
        public List<String> findActiveSessionIds(long userId) {
            return sessions.values().stream()
                    .filter(s -> s.userId() == userId && s.isActive())
                    .map(AuthSession::id)
                    .toList();
        }
    }

    static class FakeTokens implements RefreshTokenRepository {
        private final Map<String, RefreshToken> tokens = new HashMap<>();

        RefreshTokenStatus statusOf(String rawToken) {
            return tokens.get(com.example.monolith.auth.domain.OpaqueTokens.sha256(rawToken))
                    .status();
        }

        @Override
        public void insert(RefreshToken token) {
            tokens.put(token.tokenHash(), token);
        }

        @Override
        public Optional<RefreshToken> findByHash(String tokenHash) {
            return Optional.ofNullable(tokens.get(tokenHash));
        }

        @Override
        public Optional<RefreshToken> lockByHash(String tokenHash) {
            return findByHash(tokenHash);
        }

        @Override
        public void markUsed(String tokenHash, Instant usedAt) {
            replaceStatus(tokens.get(tokenHash), RefreshTokenStatus.USED);
        }

        @Override
        public int revokeActiveOfSession(String sessionId) {
            List<RefreshToken> active = tokens.values().stream()
                    .filter(t -> t.sessionId().equals(sessionId) && t.isActive())
                    .toList();
            active.forEach(t -> replaceStatus(t, RefreshTokenStatus.REVOKED));
            return active.size();
        }

        private void replaceStatus(RefreshToken token, RefreshTokenStatus status) {
            tokens.put(
                    token.tokenHash(),
                    new RefreshToken(
                            token.tokenHash(), token.sessionId(), status, token.expiresAt(), token.createdAt()));
        }
    }
}
