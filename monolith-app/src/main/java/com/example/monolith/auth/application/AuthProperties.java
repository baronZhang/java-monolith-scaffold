package com.example.monolith.auth.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 认证配置，前缀 {@code app.auth}。
 *
 * @param accessTokenTtl access token 有效期：短，被盗了也很快失效（和 Vue Mock 一样是 5 分钟）
 * @param refreshTokenTtl refresh token 有效期：长，用户不用频繁登录
 * @param jwtSecret HS256 签名密钥，至少 32 字节；生产环境必须从环境变量或密钥管理服务注入，不能写在配置文件里
 * @param issuer JWT 的签发方（iss），校验时会核对
 * @param revocationStore 吊销表存在哪：caffeine（单实例）或 redis（多实例必须用它）
 */
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
        @DefaultValue("5m") Duration accessTokenTtl,
        @DefaultValue("7d") Duration refreshTokenTtl,
        String jwtSecret,
        @DefaultValue("monolith-scaffold") String issuer,
        @DefaultValue("caffeine") String revocationStore) {}
