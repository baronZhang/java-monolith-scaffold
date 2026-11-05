package com.example.monolith.shared.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

/**
 * 缓存配置：业务代码只用 Spring Cache 注解（@Cacheable / @CacheEvict），底层用哪种缓存由 app.cache.store 决定。
 *
 * <p>三个"防"落在这里：
 * <ul>
 *   <li>穿透：允许缓存空值（allowNullValues），空值过期时间更短；</li>
 *   <li>击穿：配合 @Cacheable(sync = true)，同一个 key 并发未命中时只有一个线程去查库
 *       （Caffeine 自带按 key 加锁；Redis 实现要靠 {@link SingleFlightCacheManager} 补上）；</li>
 *   <li>雪崩：每条数据的过期时间加随机抖动，见 {@link CacheTtlPolicy}。</li>
 * </ul>
 *
 * <p>两种实现都包了一层"事务感知"：在事务里写缓存、删缓存，会推迟到事务提交之后才真正执行。
 * 否则会出现"先删了缓存、事务还没提交，别的请求把旧数据又读进缓存"的不一致。
 */
@Configuration(proxyBeanMethods = false)
@EnableCaching
public class CacheConfiguration {

    /** 默认：Caffeine 进程内缓存。读取是纳秒级，但每个实例各有一份，多实例部署时实例之间会不一致。 */
    @Bean
    @ConditionalOnProperty(name = "app.cache.store", havingValue = "caffeine", matchIfMissing = true)
    public CacheManager caffeineCacheManager(AppCacheProperties properties) {
        CacheTtlPolicy policy = new CacheTtlPolicy(properties);
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setAllowNullValues(true);
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(properties.maximumSize())
                .expireAfter(new PolicyExpiry(policy))
                .recordStats());
        return new TransactionAwareCacheManagerProxy(manager);
    }

    /**
     * redis profile：多实例共享一份缓存。
     * 值用 JSON 序列化而不是 JDK 序列化：可读、跨语言、不受类结构变化影响；
     * 需要记录类型信息才能反序列化回原来的类，这里用白名单限定只允许本项目和 JDK 的类型——
     * 千万不要用 enableUnsafeDefaultTyping()，那会给反序列化漏洞（gadget 链）开口子。
     */
    @Bean
    @ConditionalOnProperty(name = "app.cache.store", havingValue = "redis")
    public CacheManager redisCacheManager(RedisConnectionFactory connectionFactory, AppCacheProperties properties) {
        CacheTtlPolicy policy = new CacheTtlPolicy(properties);
        BasicPolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.example.monolith.")
                .allowIfSubType("java.util.")
                .allowIfSubType("java.math.")
                .allowIfSubType("java.time.")
                .build();
        GenericJacksonJsonRedisSerializer serializer = GenericJacksonJsonRedisSerializer.builder()
                .enableSpringCacheNullValueSupport()
                .enableDefaultTyping(typeValidator)
                .build();
        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                // key 形如 monolith:product::1，和其他应用共用一个 Redis 时不会冲突
                .prefixCacheNameWith("monolith:")
                .entryTtl((key, value) -> policy.ttlFor(value))
                .serializeValuesWith(SerializationPair.fromSerializer(serializer));
        // 立即写：Spring Data Redis 4 搭配 Lettuce 时，put / evict 默认是异步执行的，写完马上读可能读不到
        // （实验里单飞的双重检查因此偶尔失效）。缓存的正确性比这点延迟重要，所以改成同步写
        RedisCacheWriter cacheWriter = RedisCacheWriter.create(connectionFactory, writer -> writer.immediateWrites());
        RedisCacheManager redisCacheManager =
                RedisCacheManager.builder(cacheWriter).cacheDefaults(defaults).build();
        redisCacheManager.afterPropertiesSet();
        // 由内到外：Redis 缓存 → 按 key 单飞（防击穿，见 SingleFlightCacheManager）→ 事务感知
        return new TransactionAwareCacheManagerProxy(new SingleFlightCacheManager(redisCacheManager));
    }

    /** 把 {@link CacheTtlPolicy} 接到 Caffeine 的"按条目过期"接口上。 */
    private record PolicyExpiry(CacheTtlPolicy policy) implements Expiry<Object, Object> {

        @Override
        public long expireAfterCreate(Object key, Object value, long currentTime) {
            return policy.ttlFor(value).toNanos();
        }

        @Override
        public long expireAfterUpdate(Object key, Object value, long currentTime, long currentDuration) {
            return policy.ttlFor(value).toNanos();
        }

        @Override
        public long expireAfterRead(Object key, Object value, long currentTime, long currentDuration) {
            // 读取不延长过期时间：否则热点数据永远不过期，也就永远读不到新值
            return currentDuration;
        }
    }
}
