package com.example.monolith.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.monolith.shared.api.PageResult;
import com.example.monolith.shared.id.SnowflakeIdGenerator;
import com.example.monolith.shared.persistence.LikeEscaper;
import com.example.monolith.support.MutableClock;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/** 共享内核里几个纯工具类的单元测试 */
class SharedKernelTest {

    @Test
    void 雪花ID在多线程下不重复且每个线程内递增() throws Exception {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1, Clock.systemUTC());
        Set<Long> all = ConcurrentHashMap.newKeySet();
        int threads = 8;
        int perThread = 20_000;
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                results.add(pool.submit(() -> {
                    long previous = -1;
                    for (int i = 0; i < perThread; i++) {
                        long id = generator.nextId();
                        if (id <= previous) {
                            return false;
                        }
                        previous = id;
                        all.add(id);
                    }
                    return true;
                }));
            }
            for (Future<Boolean> result : results) {
                assertThat(result.get()).isTrue();
            }
        }
        assertThat(all).hasSize(threads * perThread);
    }

    @Test
    void 时钟大幅回拨时拒绝生成ID() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-27T00:00:00Z"));
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1, clock);
        generator.nextId();

        clock.advance(Duration.ofMillis(-100));

        assertThatThrownBy(generator::nextId).hasMessageContaining("时钟回拨了 100 毫秒");
    }

    @Test
    void 机器号超出10位范围时启动失败() {
        assertThatThrownBy(() -> new SnowflakeIdGenerator(1024, Clock.systemUTC()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void LIKE通配符被转义() {
        assertThat(LikeEscaper.escape("100%_a\\b")).isEqualTo("100\\%\\_a\\\\b");
        assertThat(LikeEscaper.escape("user05")).isEqualTo("user05");
    }

    @Test
    void 分页参数被纠正到合法范围() {
        assertThat(PageResult.normalizePage(null)).isEqualTo(1);
        assertThat(PageResult.normalizePage(-3L)).isEqualTo(1);
        assertThat(PageResult.normalizePageSize(null)).isEqualTo(10);
        assertThat(PageResult.normalizePageSize(0L)).isEqualTo(10);
        assertThat(PageResult.normalizePageSize(100_000L)).isEqualTo(100);
    }
}
