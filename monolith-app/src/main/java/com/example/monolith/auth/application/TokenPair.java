package com.example.monolith.auth.application;

/**
 * 登录、刷新接口返回的一对 token，字段和前端 {@code src/types/auth.ts} 里的 TokenPair 一致。
 *
 * @param expiresIn access token 的有效期，单位秒
 */
public record TokenPair(String accessToken, String refreshToken, long expiresIn) {

    /** 不在 toString 里输出 token 原文，避免被日志意外记录 */
    @Override
    public String toString() {
        return "TokenPair[expiresIn=" + expiresIn + "]";
    }
}
