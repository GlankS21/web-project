package com.shop.auth.repository;

import com.shop.auth.entity.UserAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    Optional<UserAccount> findByEmail(String email);

    /**
     * Row lock serializing concurrent password reset requests of one account (cooldown check).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from UserAccount a where a.email = :email")
    Optional<UserAccount> findByEmailForUpdate(@Param("email") String email);

    boolean existsByEmail(String email);

    Page<UserAccount> findByEmailContainingIgnoreCase(String email, Pageable pageable);
}
