package com.example.monolith.shared.id;

import java.time.Clock;
import java.time.Instant;

/**
 * 雪花算法 ID：64 位 = 1 位符号 + 41 位毫秒时间戳 + 10 位机器号 + 12 位序列号。
 *
 * <p>用在订单号上，原因：
 * <ul>
 *   <li>不依赖数据库：先生成 ID 再落库，预占库存的记录可以直接引用订单号；</li>
 *   <li>趋势递增：作为 InnoDB 主键时，新数据总是追加在 B+ 树的右边，不会频繁页分裂；</li>
 *   <li>不暴露业务量：自增 ID 相减就能估出一天的订单量，雪花 ID 不行。</li>
 * </ul>
 *
 * <p>代价：依赖时钟。时钟回拨会产生重复 ID，这里的处理是小幅回拨就等一等，大幅回拨直接报错。
 * 多实例部署时每个实例的机器号必须不同（配置 app.id.worker-id），否则会重复。
 *
 * <p>JSON 里要以字符串输出：64 位整数超过了 JavaScript Number 的安全范围（2^53），前端会丢精度。
 */
public class SnowflakeIdGenerator {

    /** 起始时间 2026-01-01T00:00:00Z：41 位毫秒数大约能用 69 年 */
    private static final long EPOCH = Instant.parse("2026-01-01T00:00:00Z").toEpochMilli();

    private static final int WORKER_ID_BITS = 10;
    private static final int SEQUENCE_BITS = 12;
    private static final long MAX_WORKER_ID = (1L << WORKER_ID_BITS) - 1;
    private static final long SEQUENCE_MASK = (1L << SEQUENCE_BITS) - 1;
    /** 容忍的时钟回拨：在这个范围内就等时钟追上来 */
    private static final long MAX_BACKWARD_MILLIS = 5;

    private final Clock clock;
    private final long workerId;
    private long lastTimestamp = -1;
    private long sequence;

    public SnowflakeIdGenerator(long workerId, Clock clock) {
        if (workerId < 0 || workerId > MAX_WORKER_ID) {
            throw new IllegalArgumentException("机器号必须在 0~" + MAX_WORKER_ID + " 之间：" + workerId);
        }
        this.workerId = workerId;
        this.clock = clock;
    }

    /** 生成下一个 ID。synchronized：同一毫秒内的序列号自增必须是原子的。 */
    public synchronized long nextId() {
        long timestamp = clock.millis();
        if (timestamp < lastTimestamp) {
            long backward = lastTimestamp - timestamp;
            if (backward > MAX_BACKWARD_MILLIS) {
                throw new IllegalStateException("时钟回拨了 " + backward + " 毫秒，拒绝生成 ID");
            }
            timestamp = waitUntil(lastTimestamp);
        }
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                // 这一毫秒的 4096 个序列号用完了，等到下一毫秒
                timestamp = waitUntil(lastTimestamp + 1);
            }
        } else {
            sequence = 0;
        }
        lastTimestamp = timestamp;
        return ((timestamp - EPOCH) << (WORKER_ID_BITS + SEQUENCE_BITS)) | (workerId << SEQUENCE_BITS) | sequence;
    }

    private long waitUntil(long target) {
        long now = clock.millis();
        while (now < target) {
            Thread.onSpinWait();
            now = clock.millis();
        }
        return now;
    }
}
