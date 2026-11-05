package com.example.monolith.labs.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.monolith.labs.LabReport;
import com.example.monolith.shared.cache.CacheConfiguration;
import com.github.benmanes.caffeine.cache.Cache;
import java.util.ArrayList;
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.transaction.TransactionAwareCacheDecorator;

/**
 * 原理实验：缓存穿透、击穿、雪崩，以及项目里用的防护手段（默认的 Caffeine 实现）。
 * 用项目真实的 CacheConfiguration，只把业务查询换成一个会计数的"慢数据库"。
 */
class CachePitfallsLabTest {

    final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(CacheConfiguration.class, CacheLabSupport.Beans.class)
            .withPropertyValues("app.cache.ttl=10m", "app.cache.ttl-jitter=2m", "app.cache.null-value-ttl=30s");

    @Test
    void 击穿_热点key并发未命中时_sync让数据库只被查一次() {
        runner.run(context -> {
            CacheLabSupport.SlowDatabase database = context.getBean(CacheLabSupport.SlowDatabase.class);

            int withSync = concurrentQueries(
                    database,
                    () -> context.getBean(CacheLabSupport.SafeCatalog.class).find(1));
            int withoutSync = concurrentQueries(
                    database,
                    () -> context.getBean(CacheLabSupport.NoSyncCatalog.class).find(1));

            LabReport.print("击穿：100 个并发请求同一个冷 key，sync=true 时数据库被查 {} 次；不加 sync 被查 {} 次", withSync, withoutSync);
            assertThat(withSync).isEqualTo(1);
            assertThat(withoutSync).isGreaterThan(1);
        });
    }

    @Test
    void 穿透_不存在的数据也缓存一个空值() {
        runner.run(context -> {
            CacheLabSupport.SlowDatabase database = context.getBean(CacheLabSupport.SlowDatabase.class);

            database.queries.set(0);
            for (int i = 0; i < 100; i++) {
                assertThat(context.getBean(CacheLabSupport.SafeCatalog.class).find(999))
                        .isEmpty();
            }
            int withNullCaching = database.queries.getAndSet(0);
            for (int i = 0; i < 100; i++) {
                context.getBean(CacheLabSupport.NoNullCachingCatalog.class).find(999);
            }
            int withoutNullCaching = database.queries.get();

            LabReport.print("穿透：同一个不存在的 id 查 100 次，缓存空值时数据库被查 {} 次；不缓存空值被查 {} 次", withNullCaching, withoutNullCaching);
            assertThat(withNullCaching).isEqualTo(1);
            assertThat(withoutNullCaching).isEqualTo(100);
        });
    }

    @Test
    void 雪崩_同一时刻写入的数据_过期时间被随机打散() {
        runner.run(context -> {
            CacheLabSupport.SafeCatalog catalog = context.getBean(CacheLabSupport.SafeCatalog.class);
            for (long id = 1; id <= 100; id++) {
                catalog.find(id);
            }
            catalog.find(999);

            Cache<Object, Object> nativeCache = nativeCaffeine(context.getBean(CacheManager.class), "lab-safe");
            var expiry = nativeCache.policy().expireVariably().orElseThrow();
            List<Integer> seconds = new ArrayList<>();
            for (long id = 1; id <= 100; id++) {
                seconds.add((int) expiry.getExpiresAfter(id, TimeUnit.SECONDS).orElseThrow());
            }
            IntSummaryStatistics stats =
                    seconds.stream().mapToInt(Integer::intValue).summaryStatistics();
            long distinct = seconds.stream().distinct().count();
            long nullValueTtl = expiry.getExpiresAfter(999L, TimeUnit.SECONDS).orElseThrow();

            LabReport.print(
                    "雪崩：同一时刻写入 100 条，剩余过期时间最短 {} 秒、最长 {} 秒，共 {} 种不同取值；空值的过期时间 {} 秒",
                    stats.getMin(),
                    stats.getMax(),
                    distinct,
                    nullValueTtl);
            // 基础 10 分钟 + 0~2 分钟的随机抖动
            assertThat(stats.getMin()).isBetween(595, 720);
            assertThat(stats.getMax()).isLessThanOrEqualTo(720);
            assertThat(distinct).isGreaterThan(50);
            assertThat(nullValueTtl).isLessThanOrEqualTo(30);
        });
    }

    /** 100 个线程同时查同一个 key，返回数据库被查询的次数 */
    static int concurrentQueries(CacheLabSupport.SlowDatabase database, Supplier<?> query) throws Exception {
        database.queries.set(0);
        CountDownLatch startGate = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(100)) {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                futures.add(pool.submit(() -> {
                    startGate.await();
                    return query.get();
                }));
            }
            startGate.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
        }
        return database.queries.get();
    }

    @SuppressWarnings("unchecked")
    static Cache<Object, Object> nativeCaffeine(CacheManager cacheManager, String name) {
        TransactionAwareCacheDecorator decorator = (TransactionAwareCacheDecorator) cacheManager.getCache(name);
        return ((CaffeineCache) decorator.getTargetCache()).getNativeCache();
    }
}
