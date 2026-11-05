package com.example.monolith.auth.application;

import java.time.Instant;

/**
 * access token 吊销表（黑名单）的端口。
 *
 * <p>JWT 是无状态的：签出去之后，服务端改数据库也收不回来。要让它提前失效，只能在校验时多查一张"吊销表"。
 * 关键的一点：每条记录只需要保存到 access token 的最长有效期为止——过了这个时间，被吊销的 token 自己也过期了。
 * 所以吊销表的大小是有上限的，不会无限增长。
 *
 * <p>单实例用 Caffeine 就行；多实例必须用 Redis，否则在 A 实例上退出登录，B 实例还认这个 token。
 */
public interface TokenRevocationStore {

    /** 吊销一个 access token（退出登录、演示接口"让 token 立即过期"）。 */
    void revokeAccessToken(String tokenId, Instant until);

    /** 吊销一个会话签发过的所有 access token。 */
    void revokeSession(String sessionId, Instant until);

    /** 吊销某个用户在 cutoff 之前签发的所有 access token（用户被禁用、被删除）。 */
    void revokeUserTokensIssuedBefore(long userId, Instant cutoff, Instant until);

    /** 校验时调用：这个 token 是否已被吊销。 */
    boolean isRevoked(String tokenId, String sessionId, long userId, Instant issuedAt);
}
