package com.example.monolith.shared.cache;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.cache.support.NullValue;

/**
 * 每条缓存数据的过期时间怎么算：Caffeine 和 Redis 两种实现共用这一份规则。
 *
 * <ul>
 *   <li>空值：用较短的固定时间（防穿透：查不到的 key 也缓存一下，挡住反复查询不存在数据的请求）；</li>
 *   <li>正常值：基础时间 + 随机抖动（防雪崩：同一时刻写入的大批数据，不会在同一时刻一起过期）。</li>
 * </ul>
 */
public final class CacheTtlPolicy {

    private final AppCacheProperties properties;

    public CacheTtlPolicy(AppCacheProperties properties) {
        this.properties = properties;
    }

    public Duration ttlFor(Object value) {
        if (value == null || value instanceof NullValue) {
            return properties.nullValueTtl();
        }
        long jitterMillis = properties.ttlJitter().toMillis();
        long extra = jitterMillis <= 0 ? 0 : ThreadLocalRandom.current().nextLong(jitterMillis + 1);
        return properties.ttl().plusMillis(extra);
    }
}
