package com.example.monolith.shared.persistence;

/**
 * 转义 LIKE 里的通配符。
 *
 * <p>用户输入 "%" 或 "_" 时，如果不转义，它们会被当成通配符：搜 "%" 等于不加条件，返回全表。
 * 参数化查询防住了 SQL 注入，但防不住这种"通配符注入"，这是两回事。
 * MyBatis-Plus 的 like() 只负责在两边拼上 %，不转义值本身，所以在这里处理。
 * 转义字符用反斜杠，MySQL 和 H2 的 LIKE 默认都认它。
 */
public final class LikeEscaper {

    private LikeEscaper() {}

    public static String escape(String keyword) {
        StringBuilder escaped = new StringBuilder(keyword.length());
        for (char ch : keyword.toCharArray()) {
            if (ch == '\\' || ch == '%' || ch == '_') {
                escaped.append('\\');
            }
            escaped.append(ch);
        }
        return escaped.toString();
    }
}
