package com.example.monolith.labs;

import com.zaxxer.hikari.HikariDataSource;
import java.util.UUID;

/**
 * 实验用的数据源：默认是 H2 内存库（MySQL 兼容模式）；设置了环境变量 LAB_MYSQL_URL 就连真实的 MySQL。
 *
 * <pre>
 * LAB_MYSQL_URL=jdbc:mysql://localhost:3307/lab LAB_MYSQL_USER=monolith LAB_MYSQL_PASSWORD=monolith \
 *   ./mvnw -pl monolith-app -am test -Dtest='OversellStrategiesLabTest,MysqlLabTest' \
 *   -Dsurefire.failIfNoSpecifiedTests=false
 * </pre>
 */
public final class LabDataSources {

    private LabDataSources() {}

    public static boolean mysqlConfigured() {
        String url = System.getenv("LAB_MYSQL_URL");
        return url != null && !url.isBlank();
    }

    public static HikariDataSource create(int poolSize) {
        HikariDataSource dataSource = new HikariDataSource();
        if (mysqlConfigured()) {
            dataSource.setJdbcUrl(System.getenv("LAB_MYSQL_URL"));
            dataSource.setUsername(System.getenv().getOrDefault("LAB_MYSQL_USER", "monolith"));
            dataSource.setPassword(System.getenv().getOrDefault("LAB_MYSQL_PASSWORD", "monolith"));
        } else {
            dataSource.setJdbcUrl("jdbc:h2:mem:lab-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        }
        dataSource.setMaximumPoolSize(poolSize);
        return dataSource;
    }

    public static String databaseName() {
        return mysqlConfigured() ? "MySQL" : "H2";
    }
}
