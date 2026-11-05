package com.example.monolith.auth.domain;

/** 会话状态 */
public enum SessionStatus {
    /** 有效 */
    ACTIVE,
    /** 已作废：退出登录、检测到重放、用户被禁用或删除 */
    REVOKED
}
