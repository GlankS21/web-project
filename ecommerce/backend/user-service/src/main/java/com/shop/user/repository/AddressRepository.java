package com.shop.user.repository;

import com.shop.user.entity.Address;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AddressRepository extends JpaRepository<Address, UUID> {

    List<Address> findAllByUserIdOrderByDefaultAddressDescCreatedAtDesc(UUID userId);

    /**
     * Scoped by owner: an address id belonging to another user behaves as not found.
     */
    Optional<Address> findByIdAndUserId(UUID id, UUID userId);

    long countByUserId(UUID userId);

    Optional<Address> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);

    /**
     * Executed immediately (not at flush time) so that the partial unique index on the default
     * address is never violated when another address becomes the default afterwards.
     */
    @Modifying(flushAutomatically = true)
    @Query("update Address a set a.defaultAddress = false where a.user.id = :userId and a.defaultAddress = true")
    int clearDefaultForUser(@Param("userId") UUID userId);
}
