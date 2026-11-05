package com.example.monolith.auth.domain;

import java.time.Instant;
import java.util.Optional;

/** refresh token 仓储（端口）。 */
public interface RefreshTokenRepository {

    void insert(RefreshToken token);

    /** 普通读取（快照读）。 */
    Optional<RefreshToken> findByHash(String tokenHash);

    /**
     * 加锁读取（当前读）。在 MySQL 的可重复读隔离级别下，普通 SELECT 读的是事务开始时的快照，
     * 看不到别的事务刚提交的"已使用"状态；加锁读一定读到最新提交的版本。
     */
    Optional<RefreshToken> lockByHash(String tokenHash);

    void markUsed(String tokenHash, Instant usedAt);

    /** 把会话里还有效的 token 全部作废，返回作废了几个。 */
    int revokeActiveOfSession(String sessionId);
}
