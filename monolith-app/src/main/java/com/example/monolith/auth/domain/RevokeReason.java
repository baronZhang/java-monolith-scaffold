package com.example.monolith.auth.domain;

/** 会话被作废的原因：排查"用户为什么被踢下线"时第一个要看的字段。 */
public enum RevokeReason {
    /** 用户主动退出 */
    LOGOUT,
    /** 已经用过的 refresh token 又被拿来刷新：可能被盗用，整个会话作废 */
    REUSE_DETECTED,
    /** 用户被禁用 */
    USER_DISABLED,
    /** 用户被删除 */
    USER_DELETED
}
