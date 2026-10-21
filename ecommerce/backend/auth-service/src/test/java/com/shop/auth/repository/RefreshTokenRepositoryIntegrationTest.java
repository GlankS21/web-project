package com.shop.auth.repository;

import com.shop.auth.config.ClockConfig;
import com.shop.auth.config.JpaAuditingConfig;
import com.shop.auth.entity.RefreshToken;
import com.shop.auth.entity.UserAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.cloud.config.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.placeholders.[admin_email]=admin@shop.local",
        "spring.flyway.placeholders.[admin_password]=AdminPass123"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Import({JpaAuditingConfig.class, ClockConfig.class})
class RefreshTokenRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private UserAccountRepository userAccountRepository;

    private UserAccount account;
    private final Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

    @BeforeEach
    void setUp() {
        account = userAccountRepository.findByEmail("admin@shop.local").orElseThrow();
    }

    @Test
    void findsTokenByHashWithRowLock() {
        refreshTokenRepository.saveAndFlush(token("hash-1", now.plus(Duration.ofDays(1))));

        assertThat(refreshTokenRepository.findByTokenHashForUpdate("hash-1")).isPresent();
        assertThat(refreshTokenRepository.findByTokenHashForUpdate("missing")).isEmpty();
    }

    @Test
    void revokesAllActiveTokensOfAnAccount() {
        refreshTokenRepository.saveAndFlush(token("a", now.plus(Duration.ofDays(1))));
        refreshTokenRepository.saveAndFlush(token("b", now.plus(Duration.ofDays(1))));

        int revoked = refreshTokenRepository.revokeAllActiveByUserAccountId(account.getId(), now);

        assertThat(revoked).isEqualTo(2);
        assertThat(refreshTokenRepository.countByUserAccountIdAndRevokedAtIsNull(account.getId())).isZero();
    }

    @Test
    void deletesOnlyExpiredTokens() {
        refreshTokenRepository.saveAndFlush(token("expired", now.minus(Duration.ofHours(1))));
        refreshTokenRepository.saveAndFlush(token("valid", now.plus(Duration.ofHours(1))));

        int deleted = refreshTokenRepository.deleteExpiredBefore(now);

        assertThat(deleted).isEqualTo(1);
        assertThat(refreshTokenRepository.findByTokenHash("valid")).isPresent();
        assertThat(refreshTokenRepository.findByTokenHash("expired")).isEmpty();
    }

    private RefreshToken token(String hash, Instant expiresAt) {
        return RefreshToken.builder()
                .userAccount(account)
                .tokenHash(hash)
                .createdAt(now)
                .expiresAt(expiresAt)
                .build();
    }
}
