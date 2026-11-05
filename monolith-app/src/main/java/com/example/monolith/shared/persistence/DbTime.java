package com.example.monolith.shared.persistence;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 时间在数据库里的存法：统一存 UTC 的"墙上时间"（DATETIME 列 ↔ Java LocalDateTime），转换只发生在仓储适配器里。
 *
 * <p>为什么不直接把 Instant 交给 JDBC 驱动：DATETIME 本身不带时区，驱动转换时会用 JVM 默认时区或连接参数里的时区，
 * 开发机、容器、数据库服务器的时区只要有一个不一样，存进去和读出来的时间就会差几个小时。
 * 这里显式用 UTC 转换，结果和任何机器的时区设置都无关。
 *
 * <p>配套的约定：SQL 里不用 NOW()（那是数据库服务器的本地时间），时间一律由应用传入（来自注入的 Clock）。
 */
public final class DbTime {

    private DbTime() {}

    public static LocalDateTime toDb(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    public static Instant fromDb(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.toInstant(ZoneOffset.UTC);
    }
}
