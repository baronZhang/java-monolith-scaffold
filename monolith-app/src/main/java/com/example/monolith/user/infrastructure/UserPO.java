package com.example.monolith.user.infrastructure;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * sys_user 表的持久化对象（PO）。
 *
 * <p>PO 和领域对象 User 分开：MyBatis-Plus 的注解、数据库的列（deleted、updated_at）只出现在这里，
 * 领域对象保持干净。代价是要写一段转换代码，见 {@link MybatisUserRepository}。
 */
@TableName("sys_user")
public class UserPO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;
    private String nickname;
    private String email;
    private String role;
    private String status;
    private String passwordHash;
    /** 逻辑删除标记：0 表示未删除，删除时写成自己的 id，这样唯一索引 (username, deleted) 不会挡住同名新用户 */
    private Long deleted;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Long getDeleted() {
        return deleted;
    }

    public void setDeleted(Long deleted) {
        this.deleted = deleted;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
