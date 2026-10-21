package com.shop.user.repository;

import com.shop.user.entity.UserProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {

    @Query("""
            select p from UserProfile p
            where lower(p.email) like lower(concat('%', :keyword, '%'))
               or lower(p.firstName) like lower(concat('%', :keyword, '%'))
               or lower(p.lastName) like lower(concat('%', :keyword, '%'))
            """)
    Page<UserProfile> search(@Param("keyword") String keyword, Pageable pageable);
}
