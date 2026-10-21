package com.shop.auth.security;

import com.shop.auth.entity.Role;
import com.shop.auth.entity.UserAccount;
import com.shop.common.security.HmacJwtSupport;
import com.shop.common.security.JwtClaimNames;
import com.shop.common.security.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Creates signed HS256 access tokens:
 * {@code sub} = account id, {@code email}, {@code roles}, {@code token_type = access}.
 */
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;

    public String generateAccessToken(UserAccount account, Instant issuedAt, Instant expiresAt) {
        List<String> roles = account.getRoles().stream()
                .map(Role::getName)
                .map(Enum::name)
                .sorted()
                .toList();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .id(UUID.randomUUID().toString())
                .issuer(jwtProperties.issuer())
                .subject(account.getId().toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim(JwtClaimNames.EMAIL, account.getEmail())
                .claim(JwtClaimNames.ROLES, roles)
                .claim(JwtClaimNames.TOKEN_TYPE, JwtClaimNames.ACCESS_TOKEN_TYPE)
                .build();
        JwsHeader header = JwsHeader.with(HmacJwtSupport.ALGORITHM).type("JWT").build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
