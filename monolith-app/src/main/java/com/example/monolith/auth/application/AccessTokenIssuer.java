package com.example.monolith.auth.application;

import java.time.Instant;
import java.util.List;

/**
 * 签发 access token 的端口。应用层只知道"签发一个 token"，不知道它是 JWT、用什么算法签名——那是基础设施层的事。
 */
public interface AccessTokenIssuer {

    IssuedAccessToken issue(Request request);

    /**
     * 签发请求。
     *
     * @param sessionId 会话 id，写进 token 的 sid 声明，吊销会话时用它找到这个会话签发的所有 access token
     */
    record Request(
            long userId,
            String username,
            List<String> permissions,
            String sessionId,
            Instant issuedAt,
            Instant expiresAt) {}

    /**
     * 签发结果。
     *
     * @param value token 原文
     * @param tokenId 唯一标识（JWT 的 jti），吊销单个 token 时用
     */
    record IssuedAccessToken(String value, String tokenId, Instant expiresAt) {}
}
