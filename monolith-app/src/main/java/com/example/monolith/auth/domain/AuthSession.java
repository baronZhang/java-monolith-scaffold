package com.example.monolith.auth.domain;

import java.time.Instant;

/**
 * 登录会话（聚合根）：一次登录对应一个会话，之后每次刷新换出来的 refresh token 都属于这个会话。
 *
 * <p>为什么要有"会话"这一层，而不是只有 refresh token 表：
 * <ul>
 *   <li>重放检测时要作废"整条链"上的所有 token，会话 id 就是这条链的标识；</li>
 *   <li>它是并发控制的边界：同一个会话里的刷新、吊销都先锁住会话这一行，彼此串行，不会出现竞态；</li>
 *   <li>access token 里带着会话 id（sid），退出登录时把会话记进吊销表，这个会话签发过的 access token 一起失效。</li>
 * </ul>
 */
public class AuthSession {

    private final String id;
    private final long userId;
    private SessionStatus status;
    private RevokeReason revokeReason;
    private final Instant createdAt;

    public AuthSession(String id, long userId, SessionStatus status, RevokeReason revokeReason, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.status = status;
        this.revokeReason = revokeReason;
        this.createdAt = createdAt;
    }

    public static AuthSession open(String id, long userId, Instant now) {
        return new AuthSession(id, userId, SessionStatus.ACTIVE, null, now);
    }

    public boolean isActive() {
        return status == SessionStatus.ACTIVE;
    }

    /** 作废会话。重复作废不报错（幂等），保留第一次的原因。 */
    public void revoke(RevokeReason reason) {
        if (status == SessionStatus.ACTIVE) {
            status = SessionStatus.REVOKED;
            revokeReason = reason;
        }
    }

    public String id() {
        return id;
    }

    public long userId() {
        return userId;
    }

    public SessionStatus status() {
        return status;
    }

    public RevokeReason revokeReason() {
        return revokeReason;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
