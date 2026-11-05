package com.example.monolith.auth.infrastructure;

import java.time.Instant;

/** 吊销表的 key 规则，Caffeine 和 Redis 两种实现共用。 */
final class RevocationKeys {

    private static final String PREFIX = "monolith:revoked:";

    private RevocationKeys() {}

    static String token(String tokenId) {
        return PREFIX + "jti:" + tokenId;
    }

    static String session(String sessionId) {
        return PREFIX + "sid:" + sessionId;
    }

    static String user(long userId) {
        return PREFIX + "user:" + userId;
    }

    /**
     * token 是否在 cutoff 之前（含同一秒）签发。JWT 的 iat 只精确到秒，所以按秒比较：
     * 和 cutoff 同一秒签发的 token 也算作废——宁可多踢一次，不能漏掉。
     */
    static boolean issuedNotAfter(Instant issuedAt, Instant cutoff) {
        return issuedAt != null && issuedAt.getEpochSecond() <= cutoff.getEpochSecond();
    }
}
