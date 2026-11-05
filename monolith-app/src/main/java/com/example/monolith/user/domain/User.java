package com.example.monolith.user.domain;

import com.example.monolith.shared.error.BusinessException;
import java.time.Instant;

/**
 * 用户（聚合根）。
 *
 * <p>领域对象只依赖 JDK 和共享内核，不认识 Spring、MyBatis、HTTP。
 * 业务规则（"不能禁用自己""不能删除自己"）写在这里，而不是散落在 Controller 或 SQL 里，
 * 所以不启动容器、不连数据库就能单元测试。
 */
public class User {

    private final long id;
    private final String username;
    private final String nickname;
    private final String email;
    private final String role;
    private UserStatus status;
    private final String passwordHash;
    private final Instant createdAt;

    public User(
            long id,
            String username,
            String nickname,
            String email,
            String role,
            UserStatus status,
            String passwordHash,
            Instant createdAt) {
        this.id = id;
        this.username = username;
        this.nickname = nickname;
        this.email = email;
        this.role = role;
        this.status = status;
        this.passwordHash = passwordHash;
        this.createdAt = createdAt;
    }

    /**
     * 启用或禁用。规则：不能修改自己的状态（防止管理员把自己锁在门外）。
     *
     * @return 状态是否真的变了；没变化就不用写库、不用发事件
     */
    public boolean changeStatus(UserStatus target, long operatorId) {
        if (id == operatorId) {
            throw BusinessException.rule("不能禁用当前登录的账号");
        }
        if (status == target) {
            return false;
        }
        status = target;
        return true;
    }

    /** 删除前的检查：不能删除自己。 */
    public void assertDeletableBy(long operatorId) {
        if (id == operatorId) {
            throw BusinessException.rule("不能删除当前登录的账号");
        }
    }

    public boolean isEnabled() {
        return status == UserStatus.ACTIVE;
    }

    public long id() {
        return id;
    }

    public String username() {
        return username;
    }

    public String nickname() {
        return nickname;
    }

    public String email() {
        return email;
    }

    public String role() {
        return role;
    }

    public UserStatus status() {
        return status;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
