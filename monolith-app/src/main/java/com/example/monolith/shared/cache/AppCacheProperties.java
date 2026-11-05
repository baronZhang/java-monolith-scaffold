package com.example.monolith.shared.cache;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 缓存配置，前缀 {@code app.cache}。
 *
 * @param store 缓存实现：caffeine（进程内，默认）或 redis（多实例共享）
 * @param ttl 正常数据的基础过期时间
 * @param ttlJitter 在基础过期时间上随机增加 0~ttlJitter，让同一批写入的数据不会在同一时刻集中过期（防雪崩）
 * @param nullValueTtl 空值（查不到的数据）的过期时间：比正常数据短，数据被补上后很快就能读到（防穿透的代价）
 * @param maximumSize Caffeine 最多缓存多少条：本地缓存必须有上限，否则就是内存泄漏
 */
@ConfigurationProperties(prefix = "app.cache")
public record AppCacheProperties(
        @DefaultValue("caffeine") String store,
        @DefaultValue("10m") Duration ttl,
        @DefaultValue("2m") Duration ttlJitter,
        @DefaultValue("30s") Duration nullValueTtl,
        @DefaultValue("10000") long maximumSize) {}
