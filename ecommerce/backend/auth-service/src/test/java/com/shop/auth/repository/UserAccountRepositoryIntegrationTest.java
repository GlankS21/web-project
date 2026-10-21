package com.shop.auth.repository;

import com.shop.auth.config.ClockConfig;
import com.shop.auth.config.JpaAuditingConfig;
import com.shop.auth.entity.RoleName;
import com.shop.auth.entity.UserAccount;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs the real Flyway migrations against PostgreSQL and validates the JPA mapping against them
 * ({@code ddl-auto=validate}). Skipped automatically when Docker is not available.
 */
@DataJpaTest(properties = {
        "spring.cloud.config.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.placeholders.[admin_email]=Admin@Shop.Local",
        "spring.flyway.placeholders.[admin_password]=AdminPass123"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Import({JpaAuditingConfig.class, ClockConfig.class})
class UserAccountRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private UserAccountRepository userAccountRepository;
    @Autowired
    private RoleRepository roleRepository;

    @Test
    void seededAdminHasBothRolesAndABcryptHashUsableBySpringSecurity() {
        UserAccount admin = userAccountRepository.findByEmail("admin@shop.local").orElseThrow();

        assertThat(admin.isEnabled()).isTrue();
        assertThat(admin.getRoles()).extracting(role -> role.getName())
                .containsExactlyInAnyOrder(RoleName.ROLE_ADMIN, RoleName.ROLE_CUSTOMER);
        assertThat(new BCryptPasswordEncoder().matches("AdminPass123", admin.getPasswordHash())).isTrue();
    }

    @Test
    void savesAccountWithAuditTimestamps() {
        UserAccount saved = userAccountRepository.saveAndFlush(newAccount("jane@example.com"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(userAccountRepository.existsByEmail("jane@example.com")).isTrue();
    }

    @Test
    void emailMustBeUnique() {
        userAccountRepository.saveAndFlush(newAccount("dup@example.com"));

        assertThatThrownBy(() -> userAccountRepository.saveAndFlush(newAccount("dup@example.com")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void searchesByEmailFragmentIgnoringCase() {
        userAccountRepository.saveAndFlush(newAccount("alice@example.com"));
        userAccountRepository.saveAndFlush(newAccount("bob@example.com"));

        assertThat(userAccountRepository.findByEmailContainingIgnoreCase("ALICE", PageRequest.of(0, 10)))
                .extracting(UserAccount::getEmail)
                .containsExactly("alice@example.com");
    }

    private UserAccount newAccount(String email) {
        return UserAccount.builder()
                .email(email)
                .passwordHash("$2a$10$abcdefghijklmnopqrstuuWkzIu3ZQ7s6rKxv1qX1qkC1cXo9e3zK")
                .enabled(true)
                .roles(new HashSet<>(roleRepository.findByNameIn(List.of(RoleName.ROLE_CUSTOMER))))
                .build();
    }
}
