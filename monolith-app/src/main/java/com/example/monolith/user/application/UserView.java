package com.example.monolith.user.application;

import com.example.monolith.user.domain.User;
import java.time.Instant;

/**
 * 用户的读模型，字段和前端 {@code src/types/user.ts} 里的 User 一致，Controller 直接把它作为响应。
 *
 * <p>为什么不在接口层再建一个一模一样的 UserResponse：接口契约和用例输出完全一致时，多一层只是复制字段。
 * 等两者真的不一样了（比如接口要脱敏、要兼容旧版本），再拆开。
 *
 * @param createdAt Jackson 输出为 ISO-8601 字符串，例如 2026-01-01T01:00:00Z
 */
public record UserView(
        long id, String username, String nickname, String email, String role, String status, Instant createdAt) {

    static UserView from(User user) {
        return new UserView(
                user.id(),
                user.username(),
                user.nickname(),
                user.email(),
                user.role(),
                user.status().value(),
                user.createdAt());
    }
}
