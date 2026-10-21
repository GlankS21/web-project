package com.shop.common.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Rejects JWTs whose {@code token_type} claim does not match the expected type,
 * so that only access tokens can be used as bearer credentials.
 */
public class TokenTypeValidator implements OAuth2TokenValidator<Jwt> {

    private final String expectedType;

    public TokenTypeValidator(String expectedType) {
        this.expectedType = expectedType;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        if (expectedType.equals(token.getClaimAsString(JwtClaimNames.TOKEN_TYPE))) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                OAuth2ErrorCodes.INVALID_TOKEN, "Token is not an access token", null));
    }
}
