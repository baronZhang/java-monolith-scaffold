package com.example.monolith.auth.domain;

import java.time.Duration;
import java.time.Instant;

/**
 * refresh token：会话链上的一环。数据库里只存它的 SHA-256 哈希，不存原文——拖库也拿不到能用的 token。
 *
 * <p>为什么用 SHA-256 而不是 BCrypt：BCrypt 慢，是为了对付"密码熵太低、可以暴力猜"；
 * refresh token 是 256 位随机数，本身无法暴力猜，用快速哈希就够了，还能按哈希值直接建唯一索引查找。
 */
public class RefreshToken {

    private final String tokenHash;
    private final String sessionId;
    private final RefreshTokenStatus status;
    private final Instant expiresAt;
    private final Instant createdAt;

    public RefreshToken(
            String tokenHash, String sessionId, RefreshTokenStatus status, Instant expiresAt, Instant createdAt) {
        this.tokenHash = tokenHash;
        this.sessionId = sessionId;
        this.status = status;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public static RefreshToken issue(String tokenHash, String sessionId, Instant now, Duration ttl) {
        return new RefreshToken(tokenHash, sessionId, RefreshTokenStatus.ACTIVE, now.plus(ttl), now);
    }

    public boolean isActive() {
        return status == RefreshTokenStatus.ACTIVE;
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public String tokenHash() {
        return tokenHash;
    }

    public String sessionId() {
        return sessionId;
    }

    public RefreshTokenStatus status() {
        return status;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
