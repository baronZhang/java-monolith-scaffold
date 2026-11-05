package com.example.monolith.auth.infrastructure;

import com.example.monolith.auth.domain.AuthSession;
import com.example.monolith.auth.domain.AuthSessionRepository;
import com.example.monolith.auth.domain.RefreshToken;
import com.example.monolith.auth.domain.RefreshTokenRepository;
import com.example.monolith.auth.domain.RefreshTokenStatus;
import com.example.monolith.auth.domain.RevokeReason;
import com.example.monolith.auth.domain.SessionStatus;
import com.example.monolith.shared.persistence.DbTime;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** 认证模块两个仓储的 MyBatis-Plus 实现。两张表总是一起用，放在一个文件里方便对照。 */
final class MybatisAuthRepositories {

    private MybatisAuthRepositories() {}

    @Repository
    static class SessionRepository implements AuthSessionRepository {

        private final AuthSessionMapper mapper;

        SessionRepository(AuthSessionMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public void insert(AuthSession session) {
            AuthSessionPO po = new AuthSessionPO();
            po.setId(session.id());
            po.setUserId(session.userId());
            po.setStatus(session.status().name());
            po.setCreatedAt(DbTime.toDb(session.createdAt()));
            po.setUpdatedAt(DbTime.toDb(session.createdAt()));
            mapper.insert(po);
        }

        @Override
        public Optional<AuthSession> lockById(String id) {
            return Optional.ofNullable(mapper.lockById(id)).map(SessionRepository::toDomain);
        }

        @Override
        public void updateStatus(AuthSession session, Instant updatedAt) {
            String reason = session.revokeReason() == null
                    ? null
                    : session.revokeReason().name();
            mapper.updateStatus(session.id(), session.status().name(), reason, DbTime.toDb(updatedAt));
        }

        @Override
        public List<String> findActiveSessionIds(long userId) {
            return mapper.findActiveIds(userId);
        }

        private static AuthSession toDomain(AuthSessionPO po) {
            return new AuthSession(
                    po.getId(),
                    po.getUserId(),
                    SessionStatus.valueOf(po.getStatus()),
                    po.getRevokeReason() == null ? null : RevokeReason.valueOf(po.getRevokeReason()),
                    DbTime.fromDb(po.getCreatedAt()));
        }
    }

    @Repository
    static class TokenRepository implements RefreshTokenRepository {

        private final RefreshTokenMapper mapper;

        TokenRepository(RefreshTokenMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public void insert(RefreshToken token) {
            RefreshTokenPO po = new RefreshTokenPO();
            po.setTokenHash(token.tokenHash());
            po.setSessionId(token.sessionId());
            po.setStatus(token.status().name());
            po.setExpiresAt(DbTime.toDb(token.expiresAt()));
            po.setCreatedAt(DbTime.toDb(token.createdAt()));
            mapper.insert(po);
        }

        @Override
        public Optional<RefreshToken> findByHash(String tokenHash) {
            return Optional.ofNullable(mapper.selectById(tokenHash)).map(TokenRepository::toDomain);
        }

        @Override
        public Optional<RefreshToken> lockByHash(String tokenHash) {
            return Optional.ofNullable(mapper.lockByHash(tokenHash)).map(TokenRepository::toDomain);
        }

        @Override
        public void markUsed(String tokenHash, Instant usedAt) {
            mapper.markUsed(tokenHash, DbTime.toDb(usedAt));
        }

        @Override
        public int revokeActiveOfSession(String sessionId) {
            return mapper.revokeActiveOfSession(sessionId);
        }

        private static RefreshToken toDomain(RefreshTokenPO po) {
            return new RefreshToken(
                    po.getTokenHash(),
                    po.getSessionId(),
                    RefreshTokenStatus.valueOf(po.getStatus()),
                    DbTime.fromDb(po.getExpiresAt()),
                    DbTime.fromDb(po.getCreatedAt()));
        }
    }
}
