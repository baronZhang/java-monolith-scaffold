package com.example.monolith.auth.infrastructure;

import com.example.monolith.auth.application.TokenRevocationStore;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/** JWT 校验链的最后一环：签名和有效期都没问题，但它在吊销表里（退出了、会话被作废了），也不让过。 */
final class RevokedTokenValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error REVOKED = new OAuth2Error("invalid_token", "令牌已失效", null);

    private final TokenRevocationStore revocations;

    RevokedTokenValidator(TokenRevocationStore revocations) {
        this.revocations = revocations;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        boolean revoked = revocations.isRevoked(
                jwt.getId(), jwt.getClaimAsString("sid"), Long.parseLong(jwt.getSubject()), jwt.getIssuedAt());
        return revoked ? OAuth2TokenValidatorResult.failure(REVOKED) : OAuth2TokenValidatorResult.success();
    }
}
