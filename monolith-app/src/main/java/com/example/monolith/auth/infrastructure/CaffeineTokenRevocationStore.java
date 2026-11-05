package com.example.monolith.auth.infrastructure;

import com.example.monolith.auth.application.TokenRevocationStore;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 吊销表的进程内实现（默认）。每条记录按自己的 until 过期，表的大小不会无限增长。
 *
 * <p>只适合单实例：多实例时每个实例各有一份，A 实例上退出登录，B 实例并不知道。
 */
@Component
@ConditionalOnProperty(name = "app.auth.revocation-store", havingValue = "caffeine", matchIfMissing = true)
class CaffeineTokenRevocationStore implements TokenRevocationStore {

    private final Clock clock;
    private final Cache<String, Entry> entries;

    CaffeineTokenRevocationStore(Clock clock) {
        this.clock = clock;
        this.entries = Caffeine.newBuilder()
                .maximumSize(1_000_000)
                .expireAfter(new UntilExpiry(clock))
                .build();
    }

    @Override
    public void revokeAccessToken(String tokenId, Instant until) {
        entries.put(RevocationKeys.token(tokenId), new Entry(null, until));
    }

    @Override
    public void revokeSession(String sessionId, Instant until) {
        entries.put(RevocationKeys.session(sessionId), new Entry(null, until));
    }

    @Override
    public void revokeUserTokensIssuedBefore(long userId, Instant cutoff, Instant until) {
        entries.put(RevocationKeys.user(userId), new Entry(cutoff, until));
    }

    @Override
    public boolean isRevoked(String tokenId, String sessionId, long userId, Instant issuedAt) {
        if (alive(entries.getIfPresent(RevocationKeys.token(tokenId)))) {
            return true;
        }
        if (sessionId != null && alive(entries.getIfPresent(RevocationKeys.session(sessionId)))) {
            return true;
        }
        Entry userCutoff = entries.getIfPresent(RevocationKeys.user(userId));
        return alive(userCutoff) && RevocationKeys.issuedNotAfter(issuedAt, userCutoff.cutoff());
    }

    private boolean alive(Entry entry) {
        // Caffeine 的过期是惰性清理的，这里再按应用时钟判断一次，测试里拨动时钟也能生效
        return entry != null && entry.until().isAfter(clock.instant());
    }

    /** cutoff 只在"按用户吊销"时有值 */
    private record Entry(Instant cutoff, Instant until) {}

    /** 每条记录按自己的 until 过期 */
    private record UntilExpiry(Clock clock) implements Expiry<String, Entry> {

        @Override
        public long expireAfterCreate(String key, Entry value, long currentTime) {
            return Math.max(0, Duration.between(clock.instant(), value.until()).toNanos());
        }

        @Override
        public long expireAfterUpdate(String key, Entry value, long currentTime, long currentDuration) {
            return expireAfterCreate(key, value, currentTime);
        }

        @Override
        public long expireAfterRead(String key, Entry value, long currentTime, long currentDuration) {
            return currentDuration;
        }
    }
}
