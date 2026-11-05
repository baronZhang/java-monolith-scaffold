package com.example.monolith.labs.stock;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.monolith.labs.LabDataSources;
import com.example.monolith.labs.LabReport;
import com.zaxxer.hikari.HikariDataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 原理实验：四种扣库存写法在并发下的表现。100 个请求抢 20 件库存，每个请求买 1 件。
 *
 * <ul>
 *   <li>NAIVE：先查后改，改的时候直接写算好的值（"读-改-写"）——会超卖；</li>
 *   <li>PESSIMISTIC：SELECT ... FOR UPDATE 先锁住再改——正确，但请求在行锁上排队；</li>
 *   <li>OPTIMISTIC：带版本号的条件更新，冲突了重试——正确，竞争激烈时大量重试；</li>
 *   <li>CONDITIONAL：一条 UPDATE ... WHERE available >= 1——正确、简单，项目里用的就是它。</li>
 * </ul>
 * "先查后改"的三种写法在查和改之间都停顿 2 毫秒，模拟真实代码里的业务处理，让并发窗口暴露出来。
 * 默认在 H2 上跑；设置 LAB_MYSQL_URL 后在 MySQL 上跑，见 {@link LabDataSources}。
 */
class OversellStrategiesLabTest {

    static final int STOCK = 20;
    static final int REQUESTS = 100;
    static final int THREADS = 20;
    static final int OPTIMISTIC_MAX_ATTEMPTS = 10;

    static HikariDataSource dataSource;
    static JdbcTemplate jdbc;
    static TransactionTemplate tx;

    @BeforeAll
    static void setUp() {
        dataSource = LabDataSources.create(THREADS);
        jdbc = new JdbcTemplate(dataSource);
        tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        jdbc.execute("DROP TABLE IF EXISTS lab_stock");
        jdbc.execute("CREATE TABLE lab_stock (id BIGINT PRIMARY KEY, available INT NOT NULL, version INT NOT NULL)");
        jdbc.update("INSERT INTO lab_stock (id, available, version) VALUES (1, 0, 0)");
    }

    @AfterAll
    static void tearDown() {
        dataSource.close();
    }

    @Test
    void 先查后改_丢失更新导致超卖() throws Exception {
        Result result = run("NAIVE（先查后改）", this::naive);
        assertThat(result.succeeded()).isGreaterThan(STOCK);
    }

    @Test
    void 悲观锁_不超卖() throws Exception {
        Result result = run("PESSIMISTIC（SELECT FOR UPDATE）", this::pessimistic);
        assertThat(result.succeeded()).isEqualTo(STOCK);
        assertThat(result.remaining()).isZero();
    }

    @Test
    void 乐观锁_不超卖_但竞争激烈时大量重试() throws Exception {
        Result result = run("OPTIMISTIC（版本号 + 重试）", this::optimistic);
        // 不超卖；但重试次数有限，竞争太激烈时可能出现"还有库存却没买到"
        assertThat(result.succeeded()).isLessThanOrEqualTo(STOCK);
        assertThat(result.remaining()).isEqualTo(STOCK - result.succeeded());
    }

    @Test
    void 条件更新_不超卖() throws Exception {
        Result result = run("CONDITIONAL（UPDATE ... WHERE available >= 1）", this::conditional);
        assertThat(result.succeeded()).isEqualTo(STOCK);
        assertThat(result.remaining()).isZero();
    }

    // ===================== 四种写法 =====================

    private boolean naive(AtomicInteger retries) {
        return Boolean.TRUE.equals(tx.execute(status -> {
            Integer available = jdbc.queryForObject("SELECT available FROM lab_stock WHERE id = 1", Integer.class);
            pause();
            if (available == null || available < 1) {
                return false;
            }
            // 把"算好的值"写回去：两个事务都读到 5、都写回 4，其中一次扣减就丢了
            jdbc.update("UPDATE lab_stock SET available = ? WHERE id = 1", available - 1);
            return true;
        }));
    }

    private boolean pessimistic(AtomicInteger retries) {
        return Boolean.TRUE.equals(tx.execute(status -> {
            Integer available =
                    jdbc.queryForObject("SELECT available FROM lab_stock WHERE id = 1 FOR UPDATE", Integer.class);
            pause();
            if (available == null || available < 1) {
                return false;
            }
            jdbc.update("UPDATE lab_stock SET available = available - 1 WHERE id = 1");
            return true;
        }));
    }

    /**
     * 每次尝试都是一个新事务：在 MySQL 可重复读下，同一个事务里重新 SELECT 读到的还是事务开始时的快照，
     * 版本号永远是旧的，重试多少次都会冲突。
     */
    private boolean optimistic(AtomicInteger retries) {
        for (int attempt = 1; attempt <= OPTIMISTIC_MAX_ATTEMPTS; attempt++) {
            Integer outcome = tx.execute(status -> {
                var row = jdbc.queryForMap("SELECT available, version FROM lab_stock WHERE id = 1");
                int available = ((Number) row.get("available")).intValue();
                int version = ((Number) row.get("version")).intValue();
                pause();
                if (available < 1) {
                    return -1;
                }
                return jdbc.update(
                        "UPDATE lab_stock SET available = ?, version = version + 1 WHERE id = 1 AND version = ?",
                        available - 1,
                        version);
            });
            if (outcome != null && outcome == 1) {
                return true;
            }
            if (outcome != null && outcome == -1) {
                return false;
            }
            retries.incrementAndGet();
        }
        return false;
    }

    private boolean conditional(AtomicInteger retries) {
        return Boolean.TRUE.equals(tx.execute(status ->
                jdbc.update("UPDATE lab_stock SET available = available - 1 WHERE id = 1 AND available >= 1") == 1));
    }

    // ===================== 实验框架 =====================

    private Result run(String name, Strategy strategy) throws Exception {
        jdbc.update("UPDATE lab_stock SET available = ?, version = 0 WHERE id = 1", STOCK);
        AtomicInteger retries = new AtomicInteger();
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();
        long start;
        try (ExecutorService pool = Executors.newFixedThreadPool(THREADS)) {
            for (int i = 0; i < REQUESTS; i++) {
                futures.add(pool.submit(() -> {
                    startGate.await();
                    return strategy.buyOne(retries);
                }));
            }
            start = System.nanoTime();
            startGate.countDown();
            int succeeded = 0;
            for (Future<Boolean> future : futures) {
                if (future.get()) {
                    succeeded++;
                }
            }
            long elapsed = (System.nanoTime() - start) / 1_000_000;
            Integer remaining = jdbc.queryForObject("SELECT available FROM lab_stock WHERE id = 1", Integer.class);
            Result result = new Result(succeeded, remaining == null ? 0 : remaining, retries.get(), elapsed);
            LabReport.print(
                    "[{}] {}：{} 个请求抢 {} 件 → 成功 {} 单，剩余库存 {}，超卖 {} 件，重试 {} 次，耗时 {} ms",
                    LabDataSources.databaseName(),
                    name,
                    REQUESTS,
                    STOCK,
                    result.succeeded(),
                    result.remaining(),
                    Math.max(0, result.succeeded() - STOCK),
                    result.retries(),
                    result.elapsedMillis());
            return result;
        }
    }

    private static void pause() {
        try {
            Thread.sleep(2);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    @FunctionalInterface
    interface Strategy {
        boolean buyOne(AtomicInteger retries);
    }

    record Result(int succeeded, int remaining, int retries, long elapsedMillis) {}
}
