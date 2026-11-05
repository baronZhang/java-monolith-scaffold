package com.example.monolith.labs.cache;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 缓存实验共用的 Bean：一个会计数的"慢数据库"，以及几种不同缓存写法的查询服务。 */
final class CacheLabSupport {

    private CacheLabSupport() {}

    /** 缓存里存的对象：不可变的 record */
    record LabProduct(long id, String name, BigDecimal price) {}

    /** 模拟数据库：每次查询耗时 50 毫秒，并记录被查了几次。id 大于 100 的商品不存在。 */
    static class SlowDatabase {

        final AtomicInteger queries = new AtomicInteger();

        Optional<LabProduct> find(long id) {
            queries.incrementAndGet();
            try {
                Thread.sleep(50);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            return id <= 100 ? Optional.of(new LabProduct(id, "商品" + id, new BigDecimal("9.90"))) : Optional.empty();
        }
    }

    /** 推荐写法：sync = true 防击穿；返回 Optional，查不到时缓存空值防穿透 */
    static class SafeCatalog {

        private final SlowDatabase database;

        SafeCatalog(SlowDatabase database) {
            this.database = database;
        }

        @Cacheable(cacheNames = "lab-safe", key = "#id", sync = true)
        public Optional<LabProduct> find(long id) {
            return database.find(id);
        }
    }

    /** 对照组：不加 sync，热点 key 过期的瞬间，所有并发请求都会去查库 */
    static class NoSyncCatalog {

        private final SlowDatabase database;

        NoSyncCatalog(SlowDatabase database) {
            this.database = database;
        }

        @Cacheable(cacheNames = "lab-no-sync", key = "#id")
        public Optional<LabProduct> find(long id) {
            return database.find(id);
        }
    }

    /** 对照组：查不到的数据不缓存（unless），同一个不存在的 id 每次都会打到数据库 */
    static class NoNullCachingCatalog {

        private final SlowDatabase database;

        NoNullCachingCatalog(SlowDatabase database) {
            this.database = database;
        }

        @Cacheable(cacheNames = "lab-no-null", key = "#id", unless = "#result == null")
        public Optional<LabProduct> find(long id) {
            return database.find(id);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(com.example.monolith.shared.cache.AppCacheProperties.class)
    static class Beans {

        @Bean
        SlowDatabase slowDatabase() {
            return new SlowDatabase();
        }

        @Bean
        SafeCatalog safeCatalog(SlowDatabase database) {
            return new SafeCatalog(database);
        }

        @Bean
        NoSyncCatalog noSyncCatalog(SlowDatabase database) {
            return new NoSyncCatalog(database);
        }

        @Bean
        NoNullCachingCatalog noNullCachingCatalog(SlowDatabase database) {
            return new NoNullCachingCatalog(database);
        }
    }
}
