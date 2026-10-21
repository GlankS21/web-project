package com.shop.user.repository;

import com.shop.user.config.ClockConfig;
import com.shop.user.config.JpaAuditingConfig;
import com.shop.user.entity.Address;
import com.shop.user.entity.UserProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real Flyway schema + {@code ddl-auto=validate}; checks the one-default-address rule enforced by
 * the partial unique index. Skipped automatically when Docker is not available.
 */
@DataJpaTest(properties = {
        "spring.cloud.config.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.placeholders.[admin_email]=admin@shop.local"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Import({JpaAuditingConfig.class, ClockConfig.class})
class AddressRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private AddressRepository addressRepository;
    @Autowired
    private UserProfileRepository userProfileRepository;

    private UserProfile user;

    @BeforeEach
    void setUp() {
        user = userProfileRepository.saveAndFlush(UserProfile.builder()
                .id(UUID.randomUUID())
                .email("jane@example.com")
                .firstName("Jane")
                .lastName("Doe")
                .build());
    }

    @Test
    void seededAdminProfileExists() {
        assertThat(userProfileRepository.findById(UUID.fromString("00000000-0000-0000-0000-000000000001")))
                .get()
                .extracting(UserProfile::getEmail)
                .isEqualTo("admin@shop.local");
    }

    @Test
    void databaseRejectsTwoDefaultAddressesForTheSameUser() {
        addressRepository.saveAndFlush(address("A", true));

        assertThatThrownBy(() -> addressRepository.saveAndFlush(address("B", true)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void clearDefaultForUserAllowsAnotherDefault() {
        addressRepository.saveAndFlush(address("A", true));

        int cleared = addressRepository.clearDefaultForUser(user.getId());
        addressRepository.saveAndFlush(address("B", true));

        assertThat(cleared).isEqualTo(1);
        assertThat(addressRepository.findAllByUserIdOrderByDefaultAddressDescCreatedAtDesc(user.getId()))
                .extracting(Address::getRecipientName)
                .first()
                .isEqualTo("B");
    }

    @Test
    void lookupIsScopedToOwner() {
        Address address = addressRepository.saveAndFlush(address("A", true));

        assertThat(addressRepository.findByIdAndUserId(address.getId(), user.getId())).isPresent();
        assertThat(addressRepository.findByIdAndUserId(address.getId(), UUID.randomUUID())).isEmpty();
        assertThat(addressRepository.countByUserId(user.getId())).isEqualTo(1);
    }

    @Test
    void searchesProfilesByNameOrEmail() {
        assertThat(userProfileRepository.search("JANE", PageRequest.of(0, 10)))
                .extracting(UserProfile::getId)
                .containsExactly(user.getId());
    }

    private Address address(String recipient, boolean isDefault) {
        Address address = new Address();
        address.setUser(user);
        address.setRecipientName(recipient);
        address.setPhoneNumber("+84901234567");
        address.setAddressLine1("12 Nguyen Hue");
        address.setCity("Ho Chi Minh City");
        address.setCountryCode("VN");
        address.setDefaultAddress(isDefault);
        return address;
    }
}
