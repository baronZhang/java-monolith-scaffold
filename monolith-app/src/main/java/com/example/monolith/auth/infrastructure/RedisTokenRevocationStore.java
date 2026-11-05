package com.example.monolith.auth.infrastructure;

import com.example.monolith.auth.application.TokenRevocationStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 吊销表的 Redis 实现：多实例部署时所有实例共享一份。每个 key 带过期时间，到期 Redis 自己删除。
 *
 * <p>Redis 故障时怎么办（fail-open 还是 fail-closed）是一个取舍：
 * 这里选择放行（fail-open）并打错误日志——吊销表只是"提前失效"的增强手段，access token 本身最多 5 分钟就过期；
 * 如果选择拒绝（fail-closed），Redis 一抖动，所有用户都会被踢下线。安全要求极高的系统可以反过来选。
 */
@Component
@ConditionalOnProperty(name = "app.auth.revocation-store", havingValue = "redis")
class RedisTokenRevocationStore implements TokenRevocationStore {

    private static final Logger log = LoggerFactory.getLogger(RedisTokenRevocationStore.class);

    private final StringRedisTemplate redis;
    private final Clock clock;

    RedisTokenRevocationStore(StringRedisTemplate redis, Clock clock) {
        this.redis = redis;
        this.clock = clock;
    }

    @Override
    public void revokeAccessToken(String tokenId, Instant until) {
        put(RevocationKeys.token(tokenId), "1", until);
    }

    @Override
    public void revokeSession(String sessionId, Instant until) {
        put(RevocationKeys.session(sessionId), "1", until);
    }

    @Override
    public void revokeUserTokensIssuedBefore(long userId, Instant cutoff, Instant until) {
        put(RevocationKeys.user(userId), String.valueOf(cutoff.getEpochSecond()), until);
    }

    @Override
    public boolean isRevoked(String tokenId, String sessionId, long userId, Instant issuedAt) {
        String sessionKey = sessionId == null ? RevocationKeys.session("-") : RevocationKeys.session(sessionId);
        try {
            // 一次 MGET 查三个 key：每个请求只多一次网络往返
            List<String> values = redis.opsForValue()
                    .multiGet(List.of(RevocationKeys.token(tokenId), sessionKey, RevocationKeys.user(userId)));
            if (values == null) {
                return false;
            }
            if (values.get(0) != null || values.get(1) != null) {
                return true;
            }
            String cutoff = values.get(2);
            return cutoff != null
                    && RevocationKeys.issuedNotAfter(issuedAt, Instant.ofEpochSecond(Long.parseLong(cutoff)));
        } catch (RuntimeException ex) {
            log.error("查询吊销表失败，按未吊销处理（fail-open）", ex);
            return false;
        }
    }

    private void put(String key, String value, Instant until) {
        Duration ttl = Duration.between(clock.instant(), until);
        if (!ttl.isNegative() && !ttl.isZero()) {
            redis.opsForValue().set(key, value, ttl);
        }
    }
}
