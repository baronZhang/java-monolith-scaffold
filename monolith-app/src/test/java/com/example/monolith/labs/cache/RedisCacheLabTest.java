package com.example.monolith.labs.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.monolith.labs.LabReport;
import com.example.monolith.shared.cache.CacheConfiguration;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.Cache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 原理实验：同样的缓存配置换成 Redis（redis profile 的实现）。
 * 本机 6379 端口有 Redis 时才运行；用 15 号库，只读写 monolith: 前缀的 key，跑完删掉。
 */
@EnabledIf("redisAvailable")
class RedisCacheLabTest {

    final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DataRedisAutoConfiguration.class))
            .withUserConfiguration(CacheConfiguration.class, CacheLabSupport.Beans.class)
            .withPropertyValues(
                    "app.cache.store=redis",
                    "app.cache.ttl=10m",
                    "app.cache.ttl-jitter=2m",
                    "app.cache.null-value-ttl=30s",
                    "spring.data.redis.database=15");

    static boolean redisAvailable() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", 6379), 300);
            return true;
        } catch (IOException ex) {
            return false;
        }
    }

    @Test
    void Redis缓存_record序列化_随机过期时间_空值_击穿保护() {
        runner.run(context -> {
            StringRedisTemplate redis = context.getBean(StringRedisTemplate.class);
            cleanUp(redis);
            try {
                CacheLabSupport.SafeCatalog catalog = context.getBean(CacheLabSupport.SafeCatalog.class);
                CacheLabSupport.SlowDatabase database = context.getBean(CacheLabSupport.SlowDatabase.class);

                // 写进 Redis 的是带类型信息的 JSON，读回来还是原来的 record
                Optional<CacheLabSupport.LabProduct> first = catalog.find(1);
                Optional<CacheLabSupport.LabProduct> cached = catalog.find(1);
                String json = redis.opsForValue().get("monolith:lab-safe::1");
                assertThat(cached).isEqualTo(first);

                for (long id = 2; id <= 50; id++) {
                    catalog.find(id);
                }
                long minTtl = Long.MAX_VALUE;
                long maxTtl = 0;
                for (long id = 1; id <= 50; id++) {
                    long ttl = redis.getExpire("monolith:lab-safe::" + id);
                    minTtl = Math.min(minTtl, ttl);
                    maxTtl = Math.max(maxTtl, ttl);
                }

                catalog.find(999);
                long nullTtl = redis.getExpire("monolith:lab-safe::999");

                LabReport.print("Redis：缓存值 {}", json);
                LabReport.print(
                        "Redis：同一时刻写入的 50 条数据，TTL 分布在 {}~{} 秒之间；空值的 TTL {} 秒；数据库一共被查 {} 次",
                        minTtl,
                        maxTtl,
                        nullTtl,
                        database.queries.get());
                assertThat(json).contains("LabProduct").contains("商品1");
                assertThat(minTtl).isBetween(595L, 720L);
                assertThat(maxTtl).isLessThanOrEqualTo(720L);
                assertThat(nullTtl).isBetween(1L, 30L);
            } finally {
                cleanUp(redis);
            }
        });
    }

    @Test
    void Redis缓存_冷key并发未命中_sync只在单个进程内生效() {
        runner.run(context -> {
            StringRedisTemplate redis = context.getBean(StringRedisTemplate.class);
            cleanUp(redis);
            try {
                CacheLabSupport.SlowDatabase database = context.getBean(CacheLabSupport.SlowDatabase.class);
                int queries = CachePitfallsLabTest.concurrentQueries(
                        database,
                        () -> context.getBean(CacheLabSupport.SafeCatalog.class).find(42));

                LabReport.print("Redis + sync=true：100 个并发请求同一个冷 key，数据库被查 {} 次（同一个进程内加锁；多实例时是每个实例各一次）", queries);
                assertThat(queries).isEqualTo(1);
            } finally {
                cleanUp(redis);
            }
        });
    }

    @Test
    void 对照组_原生RedisCache的sync路径不加锁_也拿不到值来算过期时间() {
        runner.run(context -> {
            StringRedisTemplate redis = context.getBean(StringRedisTemplate.class);
            cleanUp(redis);
            try {
                // 不经过 SingleFlightCacheManager，直接用 Spring Data Redis 默认的（非 locking）缓存
                RedisCacheManager raw = RedisCacheManager.builder(context.getBean(RedisConnectionFactory.class))
                        .cacheDefaults(RedisCacheConfiguration.defaultCacheConfig()
                                .prefixCacheNameWith("monolith:")
                                .entryTtl((key, value) ->
                                        value == null ? Duration.ofSeconds(30) : Duration.ofMinutes(10)))
                        .build();
                raw.afterPropertiesSet();
                Cache cache = raw.getCache("lab-raw");
                CacheLabSupport.SlowDatabase database = context.getBean(CacheLabSupport.SlowDatabase.class);

                // @Cacheable(sync = true) 最终调用的就是 cache.get(key, valueLoader)。
                // 默认配置用 JDK 序列化，缓存值得是 Serializable，这里只缓存商品名（字符串）
                int queries = CachePitfallsLabTest.concurrentQueries(
                        database,
                        () -> cache.get(
                                42L,
                                () -> database.find(42)
                                        .map(CacheLabSupport.LabProduct::name)
                                        .orElse(null)));
                long ttl = redis.getExpire("monolith:lab-raw::42");

                LabReport.print(
                        "对照组（原生 RedisCache，非 locking）：100 个并发 get(key, loader)，数据库被查 {} 次；"
                                + "TtlFunction 拿到的值是 null，按空值规则只给了 {} 秒 TTL",
                        queries,
                        ttl);
                assertThat(queries).isGreaterThan(1);
                assertThat(ttl).isLessThanOrEqualTo(30);
            } finally {
                cleanUp(redis);
            }
        });
    }

    /** 实验专用库里 key 很少，直接用 KEYS；生产环境不要用 KEYS（会阻塞 Redis），要用 SCAN */
    private static void cleanUp(StringRedisTemplate redis) {
        Set<String> keys = redis.keys("monolith:lab-*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }
}
