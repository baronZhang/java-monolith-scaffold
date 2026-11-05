package com.example.monolith.auth.infrastructure;

import com.example.monolith.auth.application.AuthProperties;
import com.example.monolith.auth.application.TokenRevocationStore;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * JWT 的签发和校验。
 *
 * <p>单体用 HS256（对称密钥）：签发方和校验方是同一个进程，共用一个密钥最简单。
 * 微服务里网关、各服务都要校验，就该换成 RS256 / ES256（非对称）：认证服务用私钥签，其他服务从 JWKS 地址拿公钥验，
 * 密钥不用分发给每个服务。
 */
@Configuration(proxyBeanMethods = false)
public class JwtConfiguration {

    /** HS256 要求密钥不短于 256 位（32 字节），太短的密钥可以被暴力破解。启动时就检查，不合格直接起不来。 */
    @Bean
    public SecretKey jwtSigningKey(AuthProperties properties) {
        String secret = properties.jwtSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("app.auth.jwt-secret 至少需要 32 字节");
        }
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
        return NimbusJwtEncoder.withSecretKey(jwtSigningKey)
                .algorithm(MacAlgorithm.HS256)
                .build();
    }

    /**
     * 校验链：签名 → 过期时间 → 签发方 → 是否在吊销表里。
     *
     * <p>时钟偏差设为 0：签发和校验在同一个进程，用的是同一个时钟。
     * 分布式环境里各机器的时钟有偏差，通常要留 30~60 秒（Spring Security 的默认值是 60 秒）。
     */
    @Bean
    public JwtDecoder jwtDecoder(
            SecretKey jwtSigningKey, AuthProperties properties, TokenRevocationStore revocations, Clock clock) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        JwtTimestampValidator timestamps = new JwtTimestampValidator(Duration.ZERO);
        timestamps.setClock(clock);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                timestamps, new JwtIssuerValidator(properties.issuer()), new RevokedTokenValidator(revocations)));
        return decoder;
    }
}
