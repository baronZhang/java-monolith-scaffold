package com.example.monolith.auth.domain;

/** refresh token 状态 */
public enum RefreshTokenStatus {
    /** 可以用来刷新 */
    ACTIVE,
    /** 已经用过一次（轮换：用一次就作废，同时签发新的） */
    USED,
    /** 随会话一起被作废 */
    REVOKED
}
