package com.example.monolith.auth.application;

import com.example.monolith.auth.domain.AuthSession;
import com.example.monolith.auth.domain.AuthSessionRepository;
import com.example.monolith.auth.domain.OpaqueTokens;
import com.example.monolith.auth.domain.RefreshToken;
import com.example.monolith.auth.domain.RefreshTokenRepository;
import com.example.monolith.auth.domain.RevokeReason;
import com.example.monolith.shared.error.BusinessException;
import com.example.monolith.shared.error.ErrorCode;
import com.example.monolith.user.UserAccount;
import com.example.monolith.user.UserApi;
import com.example.monolith.user.UserProfile;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 认证用例：登录、刷新、退出、作废会话。
 *
 * <p>refresh token 的三条规则（OAuth 2.0 安全最佳实践推荐的做法）：
 * <ol>
 *   <li>轮换：每个 refresh token 只能用一次，用完立即作废，同时签发新的；</li>
 *   <li>重放检测：已经用过的 token 又被拿来刷新，说明它可能被盗了——作废整个会话，攻击者和用户都得重新登录；</li>
 *   <li>并发安全：同一会话的刷新先锁住会话行，串行执行。并发拿同一个 token 刷新，只有第一个成功，其余的按重放处理。</li>
 * </ol>
 * 第 3 条也解释了为什么前端必须保证"并发 401 时只刷新一次"：否则后到的刷新请求会被当成重放，把用户踢下线。
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    /** BCrypt 只处理前 72 字节，更长的密码直接拒绝，也顺带防住"超长密码拖慢哈希计算"的攻击 */
    private static final int MAX_PASSWORD_BYTES = 72;

    private final UserApi userApi;
    private final PasswordEncoder passwordEncoder;
    private final AuthSessionRepository sessions;
    private final RefreshTokenRepository refreshTokens;
    private final TokenRevocationStore revocations;
    private final AccessTokenIssuer accessTokenIssuer;
    private final AuthProperties properties;
    private final Clock clock;
    private final Counter refreshRequests;
    /** 用户名不存在时也要跑一次 BCrypt：否则"用户不存在"比"密码错误"响应快得多，攻击者能据此枚举用户名 */
    private final String dummyPasswordHash;

    public AuthService(
            UserApi userApi,
            PasswordEncoder passwordEncoder,
            AuthSessionRepository sessions,
            RefreshTokenRepository refreshTokens,
            TokenRevocationStore revocations,
            AccessTokenIssuer accessTokenIssuer,
            AuthProperties properties,
            Clock clock,
            MeterRegistry meterRegistry) {
        this.userApi = userApi;
        this.passwordEncoder = passwordEncoder;
        this.sessions = sessions;
        this.refreshTokens = refreshTokens;
        this.revocations = revocations;
        this.accessTokenIssuer = accessTokenIssuer;
        this.properties = properties;
        this.clock = clock;
        // 刷新次数用 Micrometer 指标记录：生产上 Prometheus 能抓到，演示接口 /api/debug/stats 读的也是它
        this.refreshRequests = Counter.builder("auth.refresh.requests")
                .description("收到的 refresh token 刷新请求数")
                .register(meterRegistry);
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public TokenPair login(String username, String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw loginFailed();
        }
        Optional<UserAccount> found = userApi.findAccountByUsername(username);
        if (found.isEmpty() || found.get().passwordHash() == null) {
            passwordEncoder.matches(password, dummyPasswordHash);
            throw loginFailed();
        }
        UserAccount account = found.get();
        if (!passwordEncoder.matches(password, account.passwordHash())) {
            throw loginFailed();
        }
        // 密码对了才告诉对方"账号被禁用"：只有账号本人能看到这个信息
        if (!account.enabled()) {
            throw BusinessException.rule("账号已被禁用，请联系管理员");
        }
        Instant now = clock.instant();
        AuthSession session = AuthSession.open(UUID.randomUUID().toString(), account.userId(), now);
        sessions.insert(session);
        return issueTokenPair(session, account, now);
    }

    /**
     * 用 refresh token 换一对新 token。
     *
     * <p>noRollbackFor：检测到重放时要作废整个会话，并且这个作废必须提交到数据库，同时还要给调用方返回 401。
     * 默认情况下抛出 RuntimeException 会把作废操作一起回滚，所以这里声明这个异常不回滚。
     */
    @Transactional(noRollbackFor = RefreshTokenRejectedException.class)
    public TokenPair refresh(String rawRefreshToken) {
        refreshRequests.increment();
        String tokenHash = OpaqueTokens.sha256(rawRefreshToken);
        RefreshToken token = refreshTokens.findByHash(tokenHash).orElseThrow(RefreshTokenRejectedException::new);

        // ① 锁住会话：同一个会话的刷新、吊销在这里排队
        AuthSession session = sessions.lockById(token.sessionId()).orElseThrow(RefreshTokenRejectedException::new);
        if (!session.isActive()) {
            throw new RefreshTokenRejectedException();
        }
        Instant now = clock.instant();

        // ② 拿到锁之后用加锁读重新读一次：前面那次普通读可能是旧快照，看不到别人刚提交的"已使用"
        RefreshToken current = refreshTokens.lockByHash(tokenHash).orElseThrow(RefreshTokenRejectedException::new);
        if (!current.isActive()) {
            // ③ 用过的 token 又来了：要么是客户端并发重复刷新，要么是被盗后重放。两者分不清，一律按被盗处理
            log.warn("检测到 refresh token 重放，作废整个会话 sessionId={} userId={}", session.id(), session.userId());
            revokeSession(session, RevokeReason.REUSE_DETECTED, now);
            throw new RefreshTokenRejectedException();
        }
        if (current.isExpired(now)) {
            throw new RefreshTokenRejectedException();
        }

        // ④ 用户可能刚被禁用、权限可能变了：刷新时重新查一次，新 token 里带的是最新的权限
        Optional<UserAccount> account = userApi.findAccount(session.userId()).filter(UserAccount::enabled);
        if (account.isEmpty()) {
            revokeSession(session, RevokeReason.USER_DISABLED, now);
            throw new RefreshTokenRejectedException();
        }
        refreshTokens.markUsed(tokenHash, now);
        return issueTokenPair(session, account.get(), now);
    }

    /** 退出登录：作废会话（连带它的 refresh token 和签发过的 access token）。 */
    @Transactional
    public void logout(String sessionId, String accessTokenId, Instant accessTokenExpiresAt) {
        Instant now = clock.instant();
        sessions.lockById(sessionId).ifPresent(session -> revokeSession(session, RevokeReason.LOGOUT, now));
        revocations.revokeAccessToken(accessTokenId, accessTokenExpiresAt);
    }

    /** 作废某个用户的全部会话：用户被禁用、被删除时由事件监听器调用。 */
    @Transactional
    public void revokeAllSessions(long userId, RevokeReason reason) {
        Instant now = clock.instant();
        for (String sessionId : sessions.findActiveSessionIds(userId)) {
            sessions.lockById(sessionId).ifPresent(session -> revokeSession(session, reason, now));
        }
        // 兜底：这个用户在此刻之前签发的 access token 全部作废，不用等它们自然过期
        revocations.revokeUserTokensIssuedBefore(userId, now, now.plus(properties.accessTokenTtl()));
        log.info("已作废用户的全部会话 userId={} reason={}", userId, reason);
    }

    @Transactional(readOnly = true)
    public UserProfile profile(long userId) {
        return userApi.findProfile(userId).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "用户不存在"));
    }

    /** 演示用：让一个 access token 立即失效，下一个请求就会收到 401，前端走无感刷新。 */
    public void expireAccessToken(String accessTokenId, Instant accessTokenExpiresAt) {
        revocations.revokeAccessToken(accessTokenId, accessTokenExpiresAt);
    }

    /** 演示用：累计收到的刷新请求数。 */
    public long refreshRequestCount() {
        return (long) refreshRequests.count();
    }

    private void revokeSession(AuthSession session, RevokeReason reason, Instant now) {
        session.revoke(reason);
        sessions.updateStatus(session, now);
        refreshTokens.revokeActiveOfSession(session.id());
        // access token 是无状态的，改数据库收不回来：把会话记进吊销表，保存到 access token 的最长有效期为止
        revocations.revokeSession(session.id(), now.plus(properties.accessTokenTtl()));
    }

    private TokenPair issueTokenPair(AuthSession session, UserAccount account, Instant now) {
        String rawRefreshToken = OpaqueTokens.generate();
        refreshTokens.insert(RefreshToken.issue(
                OpaqueTokens.sha256(rawRefreshToken), session.id(), now, properties.refreshTokenTtl()));
        AccessTokenIssuer.IssuedAccessToken accessToken = accessTokenIssuer.issue(new AccessTokenIssuer.Request(
                account.userId(),
                account.username(),
                account.permissions(),
                session.id(),
                now,
                now.plus(properties.accessTokenTtl())));
        return new TokenPair(
                accessToken.value(),
                rawRefreshToken,
                properties.accessTokenTtl().toSeconds());
    }

    private static BusinessException loginFailed() {
        return new BusinessException(ErrorCode.LOGIN_FAILED, "用户名或密码错误");
    }
}
