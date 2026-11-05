package com.example.monolith.shared.cache;

import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

/**
 * 给 Redis 缓存补上"同一个 key 只让一个线程回源"（@Cacheable(sync = true) 的语义）。
 *
 * <p>为什么需要它（实验里实际踩到的）：Spring Data Redis 3.x 起，RedisCache 的 get(key, valueLoader)
 * 不再在进程内加锁；只有换成 "locking" 的 RedisCacheWriter 才会加锁，而那把锁是 Redis 里的
 * "缓存名~lock"——锁的是整个缓存，一个 key 在回源时，同一个缓存里所有 key 的读写都要等它。
 * 另外，这条路径计算过期时间时拿不到值（传给 TtlFunction 的是 null），随机过期、空值短过期都失效了。
 *
 * <p>这里的做法：每个 key 一把进程内的锁，双重检查后才回源，回源结果用普通的 put 写回
 * （put 路径里 TtlFunction 能拿到真实的值）。代价：锁只在单个实例内有效，N 个实例最多回源 N 次——
 * 从"上千次"降到"实例数次"，通常已经够了；要做到全局只回源一次，得用分布式锁或者逻辑过期。
 */
final class SingleFlightCacheManager implements CacheManager {

    private final CacheManager delegate;
    private final ConcurrentMap<String, Cache> caches = new ConcurrentHashMap<>();

    SingleFlightCacheManager(CacheManager delegate) {
        this.delegate = delegate;
    }

    @Override
    public Cache getCache(String name) {
        return caches.computeIfAbsent(name, key -> {
            Cache target = delegate.getCache(key);
            return target == null ? null : new SingleFlightCache(target);
        });
    }

    @Override
    public Collection<String> getCacheNames() {
        return delegate.getCacheNames();
    }

    /** 只改写 get(key, valueLoader)，其余操作原样转给被包装的缓存 */
    static final class SingleFlightCache implements Cache {

        private final Cache target;
        private final ConcurrentMap<Object, ReentrantLock> locks = new ConcurrentHashMap<>();

        SingleFlightCache(Cache target) {
            this.target = target;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T get(Object key, Callable<T> valueLoader) {
            ValueWrapper cached = target.get(key);
            if (cached != null) {
                return (T) cached.get();
            }
            ReentrantLock lock = locks.computeIfAbsent(key, k -> new ReentrantLock());
            lock.lock();
            try {
                // 双重检查：排队等锁的这段时间里，前一个线程可能已经把值放进缓存了
                cached = target.get(key);
                if (cached != null) {
                    return (T) cached.get();
                }
                T value;
                try {
                    value = valueLoader.call();
                } catch (Exception ex) {
                    throw new ValueRetrievalException(key, valueLoader, ex);
                }
                target.put(key, value);
                return value;
            } finally {
                lock.unlock();
                locks.remove(key, lock);
            }
        }

        @Override
        public String getName() {
            return target.getName();
        }

        @Override
        public Object getNativeCache() {
            return target.getNativeCache();
        }

        @Override
        public ValueWrapper get(Object key) {
            return target.get(key);
        }

        @Override
        public <T> T get(Object key, Class<T> type) {
            return target.get(key, type);
        }

        @Override
        public void put(Object key, Object value) {
            target.put(key, value);
        }

        @Override
        public ValueWrapper putIfAbsent(Object key, Object value) {
            return target.putIfAbsent(key, value);
        }

        @Override
        public void evict(Object key) {
            target.evict(key);
        }

        @Override
        public boolean evictIfPresent(Object key) {
            return target.evictIfPresent(key);
        }

        @Override
        public void clear() {
            target.clear();
        }

        @Override
        public boolean invalidate() {
            return target.invalidate();
        }
    }
}
