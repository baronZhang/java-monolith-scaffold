package com.example.monolith.labs.db;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.monolith.labs.LabDataSources;
import com.example.monolith.labs.LabReport;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 原理实验：MySQL 的索引（EXPLAIN）、隔离级别、MVCC、间隙锁、死锁。只在设置了 LAB_MYSQL_URL 时运行
 * （H2 的执行计划和锁机制与 MySQL 不同，在 H2 上做这些实验没有意义）。
 *
 * <p>本机实测用的是 docker compose 起的 MySQL 8.0.30，数据量 20 万行。
 */
@EnabledIf("com.example.monolith.labs.LabDataSources#mysqlConfigured")
class MysqlLabTest {

    static HikariDataSource dataSource;
    static JdbcTemplate jdbc;

    @BeforeAll
    static void setUp() throws SQLException {
        dataSource = LabDataSources.create(4);
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP TABLE IF EXISTS lab_orders");
        jdbc.execute("""
                CREATE TABLE lab_orders (
                    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
                    user_id    BIGINT         NOT NULL,
                    status     VARCHAR(20)    NOT NULL,
                    amount     DECIMAL(12, 2) NOT NULL,
                    remark     VARCHAR(32)    NOT NULL,
                    created_at DATETIME(3)    NOT NULL,
                    INDEX idx_status_created (status, created_at),
                    INDEX idx_user_created (user_id, created_at),
                    INDEX idx_remark (remark)
                )""");
        // 递归 CTE 一次生成 20 万行：2% 待支付、约 14% 已取消、其余已支付；每分钟一单
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("SET SESSION cte_max_recursion_depth = 1000000");
            statement.execute("""
                    INSERT INTO lab_orders (user_id, status, amount, remark, created_at)
                    WITH RECURSIVE seq(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 200000)
                    SELECT n % 5000,
                           IF(n % 50 = 0, 'PENDING_PAYMENT', IF(n % 7 = 0, 'CANCELLED', 'PAID')),
                           n % 1000,
                           CONCAT('R', LPAD(n, 7, '0')),
                           TIMESTAMP('2026-01-01 00:00:00') + INTERVAL n MINUTE
                    FROM seq""");
            statement.execute("ANALYZE TABLE lab_orders");
        }
        jdbc.execute("DROP TABLE IF EXISTS lab_account");
        jdbc.execute("CREATE TABLE lab_account (id BIGINT PRIMARY KEY, balance INT NOT NULL)");
    }

    @AfterAll
    static void tearDown() {
        dataSource.close();
    }

    // ===================== 索引与 EXPLAIN =====================

    @Test
    void EXPLAIN_好的查询和坏的查询() {
        Map<String, String> queries = new java.util.LinkedHashMap<>();
        queries.put(
                "超时扫描：status 等值 + created_at 范围 + 按 created_at 排序",
                "SELECT id FROM lab_orders WHERE status = 'PENDING_PAYMENT' AND created_at < '2026-03-01'"
                        + " ORDER BY created_at LIMIT 100");
        queries.put("跳过联合索引的最左列：只按 created_at 查", "SELECT id FROM lab_orders WHERE created_at < '2026-01-02'");
        queries.put(
                "我的订单：user_id 等值 + 按 created_at 倒序取 10 条",
                "SELECT * FROM lab_orders WHERE user_id = 42 ORDER BY created_at DESC LIMIT 10");
        queries.put("索引列等值查询", "SELECT id FROM lab_orders WHERE remark = 'R0001234'");
        queries.put("索引列套函数", "SELECT id FROM lab_orders WHERE LOWER(remark) = 'r0001234'");
        queries.put("隐式类型转换：字符串列和数字比较", "SELECT id FROM lab_orders WHERE remark = 1234");
        queries.put("前缀匹配 LIKE 'R00012%'", "SELECT id, user_id FROM lab_orders WHERE remark LIKE 'R00012%'");
        queries.put("后缀匹配 LIKE '%1234'", "SELECT id, user_id FROM lab_orders WHERE remark LIKE '%1234'");
        queries.put(
                "覆盖索引：只查索引里有的列",
                "SELECT status, created_at FROM lab_orders WHERE status = 'PAID' AND created_at < '2026-01-03'");
        queries.put("回表：查了索引里没有的列", "SELECT * FROM lab_orders WHERE status = 'PAID' AND created_at < '2026-01-03'");

        Map<String, Map<String, Object>> plans = new java.util.LinkedHashMap<>();
        queries.forEach((label, sql) -> {
            Map<String, Object> plan = jdbc.queryForMap("EXPLAIN " + sql);
            plans.put(label, plan);
            LabReport.print(
                    "EXPLAIN {}：type={}，key={}，rows={}，Extra={}",
                    label,
                    plan.get("type"),
                    plan.get("key"),
                    plan.get("rows"),
                    plan.get("Extra"));
        });

        Map<String, Object> scan = plans.get("超时扫描：status 等值 + created_at 范围 + 按 created_at 排序");
        assertThat(scan.get("key")).isEqualTo("idx_status_created");
        assertThat(String.valueOf(scan.get("Extra"))).doesNotContain("filesort");
        assertThat(plans.get("我的订单：user_id 等值 + 按 created_at 倒序取 10 条").get("key"))
                .isEqualTo("idx_user_created");
        assertThat(plans.get("索引列等值查询").get("type")).isEqualTo("ref");
        assertThat(plans.get("索引列套函数").get("type")).isIn("ALL", "index");
        assertThat(plans.get("隐式类型转换：字符串列和数字比较").get("type")).isIn("ALL", "index");
        assertThat(plans.get("前缀匹配 LIKE 'R00012%'").get("type")).isEqualTo("range");
        assertThat(String.valueOf(plans.get("覆盖索引：只查索引里有的列").get("Extra"))).contains("Using index");
    }

    // ===================== 隔离级别与 MVCC =====================

    @Test
    void 可重复读_普通SELECT读快照_加锁读和UPDATE读最新() throws Exception {
        resetAccount(100);
        try (Connection t1 = transaction("REPEATABLE READ");
                Connection t2 = transaction("REPEATABLE READ")) {
            int first = balance(t1, "");
            update(t2, "UPDATE lab_account SET balance = 90 WHERE id = 1");
            t2.commit();
            int snapshot = balance(t1, "");
            int current = balance(t1, " FOR UPDATE");
            update(t1, "UPDATE lab_account SET balance = balance - 1 WHERE id = 1");
            int afterOwnUpdate = balance(t1, "");
            t1.commit();

            LabReport.print(
                    "可重复读：T1 第一次读 {}；T2 改成 90 并提交；T1 普通读 {}（快照），加锁读 {}（当前读），" + "T1 执行 balance - 1 后再读 {}",
                    first,
                    snapshot,
                    current,
                    afterOwnUpdate);
            assertThat(snapshot).isEqualTo(100);
            assertThat(current).isEqualTo(90);
            assertThat(afterOwnUpdate).isEqualTo(89);
        }
    }

    @Test
    void 读已提交_同一个事务里两次读结果不同() throws Exception {
        resetAccount(100);
        try (Connection t1 = transaction("READ COMMITTED");
                Connection t2 = transaction("READ COMMITTED")) {
            int first = balance(t1, "");
            update(t2, "UPDATE lab_account SET balance = 90 WHERE id = 1");
            t2.commit();
            int second = balance(t1, "");
            t1.commit();

            LabReport.print("读已提交：T1 第一次读 {}，T2 提交后 T1 再读 {}（不可重复读：每条语句都用新的快照）", first, second);
            assertThat(second).isEqualTo(90);
        }
    }

    @Test
    void 丢失更新_两个事务读同一个值再各自写回() throws Exception {
        resetAccount(100);
        try (Connection t1 = transaction("REPEATABLE READ");
                Connection t2 = transaction("REPEATABLE READ")) {
            int read1 = balance(t1, "");
            int read2 = balance(t2, "");
            update(t1, "UPDATE lab_account SET balance = " + (read1 - 10) + " WHERE id = 1");
            t1.commit();
            update(t2, "UPDATE lab_account SET balance = " + (read2 - 10) + " WHERE id = 1");
            t2.commit();
            Integer result = jdbc.queryForObject("SELECT balance FROM lab_account WHERE id = 1", Integer.class);

            LabReport.print("丢失更新：两个事务都读到 {}，各扣 10 后写回算好的值，最终余额 {}（应该是 80）——这就是超卖的根因", read1, result);
            assertThat(result).isEqualTo(90);
        }
    }

    @Test
    void 间隙锁_可重复读下阻止幻读插入_读已提交下不阻止() throws Exception {
        boolean blockedUnderRepeatableRead = insertIntoLockedRange("REPEATABLE READ");
        boolean blockedUnderReadCommitted = insertIntoLockedRange("READ COMMITTED");

        LabReport.print(
                "间隙锁：T1 用 FOR UPDATE 锁住 id 10~30，T2 插入 id=15——可重复读下被挡住（锁等待超时）：{}；" + "读已提交下被挡住：{}",
                blockedUnderRepeatableRead,
                blockedUnderReadCommitted);
        assertThat(blockedUnderRepeatableRead).isTrue();
        assertThat(blockedUnderReadCommitted).isFalse();
    }

    private boolean insertIntoLockedRange(String isolation) throws Exception {
        jdbc.execute("DROP TABLE IF EXISTS lab_gap");
        jdbc.execute("CREATE TABLE lab_gap (id BIGINT PRIMARY KEY, name VARCHAR(16))");
        jdbc.update("INSERT INTO lab_gap VALUES (10, 'a'), (20, 'b'), (30, 'c')");
        try (Connection t1 = transaction(isolation);
                Connection t2 = transaction(isolation)) {
            try (Statement statement = t1.createStatement()) {
                statement
                        .executeQuery("SELECT * FROM lab_gap WHERE id BETWEEN 10 AND 30 FOR UPDATE")
                        .close();
            }
            try (Statement statement = t2.createStatement()) {
                statement.execute("SET SESSION innodb_lock_wait_timeout = 1");
                statement.executeUpdate("INSERT INTO lab_gap VALUES (15, 'phantom')");
                t2.rollback();
                return false;
            } catch (SQLException ex) {
                // 1205：Lock wait timeout exceeded
                assertThat(ex.getErrorCode()).isEqualTo(1205);
                t2.rollback();
                return true;
            } finally {
                t1.rollback();
            }
        }
    }

    @Test
    void 死锁_两个事务以相反顺序加锁_MySQL自动选一个回滚() throws Exception {
        List<String> outcomes = lockTwoRows(new long[] {1, 2}, new long[] {2, 1});
        List<String> sorted = lockTwoRows(new long[] {1, 2}, new long[] {1, 2});

        LabReport.print("死锁：相反顺序加锁 → {}；按同一顺序加锁 → {}", outcomes, sorted);
        assertThat(outcomes).anyMatch(outcome -> outcome.contains("1213"));
        assertThat(sorted).allMatch(outcome -> outcome.equals("成功"));
    }

    /** 两个事务各锁两行，中间用栅栏对齐：确保都拿到了第一把锁之后再去抢第二把 */
    private List<String> lockTwoRows(long[] first, long[] second) throws Exception {
        jdbc.execute("DROP TABLE IF EXISTS lab_deadlock");
        jdbc.execute("CREATE TABLE lab_deadlock (id BIGINT PRIMARY KEY, n INT NOT NULL)");
        jdbc.update("INSERT INTO lab_deadlock VALUES (1, 0), (2, 0)");
        CyclicBarrier barrier = new CyclicBarrier(2);
        List<String> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            List<Future<String>> futures = new ArrayList<>();
            for (long[] order : List.of(first, second)) {
                futures.add(pool.submit(() -> {
                    try (Connection connection = transaction("REPEATABLE READ");
                            Statement statement = connection.createStatement()) {
                        statement.execute("SET SESSION innodb_lock_wait_timeout = 5");
                        statement.executeUpdate("UPDATE lab_deadlock SET n = n + 1 WHERE id = " + order[0]);
                        awaitQuietly(barrier);
                        try {
                            statement.executeUpdate("UPDATE lab_deadlock SET n = n + 1 WHERE id = " + order[1]);
                            connection.commit();
                            return "成功";
                        } catch (SQLException ex) {
                            connection.rollback();
                            return "失败（错误码 " + ex.getErrorCode() + "）";
                        }
                    }
                }));
            }
            for (Future<String> future : futures) {
                results.add(future.get());
            }
        }
        return results;
    }

    // ===================== 小工具 =====================

    private static void resetAccount(int balance) {
        jdbc.update("DELETE FROM lab_account");
        jdbc.update("INSERT INTO lab_account VALUES (1, ?)", balance);
    }

    private static Connection transaction(String isolation) throws SQLException {
        Connection connection = dataSource.getConnection();
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET SESSION TRANSACTION ISOLATION LEVEL " + isolation);
        }
        return connection;
    }

    private static int balance(Connection connection, String suffix) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery("SELECT balance FROM lab_account WHERE id = 1" + suffix)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static void update(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private static void awaitQuietly(CyclicBarrier barrier) {
        try {
            barrier.await(3, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception ex) {
            // 按相同顺序加锁时，第二个事务会卡在第一把锁上到不了栅栏，超时后继续即可
        }
    }
}
