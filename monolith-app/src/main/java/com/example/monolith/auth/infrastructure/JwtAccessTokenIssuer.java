package com.example.monolith.auth.infrastructure;

import com.example.monolith.auth.application.AccessTokenIssuer;
import com.example.monolith.auth.application.AuthProperties;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/**
 * 用 JWT 实现 access token。
 *
 * <p>声明（claims）：iss 签发方、sub 用户 id、jti 唯一标识、iat / exp 签发和过期时间，
 * 以及自定义的 username、perms（权限码）、sid（会话 id）。
 * 权限放进 token：校验时不用查库就知道能做什么；代价是权限变更要等 token 过期或刷新后才生效（最多 5 分钟），
 * 禁用用户这种需要立即生效的情况，靠吊销表兜底。
 */
@Component
class JwtAccessTokenIssuer implements AccessTokenIssuer {

    private final JwtEncoder encoder;
    private final AuthProperties properties;

    JwtAccessTokenIssuer(JwtEncoder encoder, AuthProperties properties) {
        this.encoder = encoder;
        this.properties = properties;
    }

    @Override
    public IssuedAccessToken issue(Request request) {
        String tokenId = UUID.randomUUID().toString();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(String.valueOf(request.userId()))
                .id(tokenId)
                .issuedAt(request.issuedAt())
                .expiresAt(request.expiresAt())
                .claim("username", request.username())
                .claim("perms", request.permissions())
                .claim("sid", request.sessionId())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedAccessToken(token, tokenId, request.expiresAt());
    }
}
