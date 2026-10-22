package com.shop.user.service.impl;

import com.shop.common.dto.PageResponse;
import com.shop.common.event.UserRegisteredEvent;
import com.shop.common.exception.ResourceNotFoundException;
import com.shop.user.dto.request.UpdateProfileRequest;
import com.shop.user.dto.response.UserProfileResponse;
import com.shop.user.entity.UserProfile;
import com.shop.user.mapper.UserProfileMapper;
import com.shop.user.repository.UserProfileRepository;
import com.shop.user.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileServiceImpl implements UserProfileService {

    static final String PROFILE_NOT_FOUND = "profile.error.not-found";

    private final UserProfileRepository userProfileRepository;
    private final UserProfileMapper userProfileMapper;

    @Override
    @Transactional
    public UserProfileResponse getOrCreateProfile(UUID userId, String email) {
        return userProfileMapper.toResponse(findOrCreate(userId, email));
    }

    @Override
    @Transactional
    public UserProfileResponse updateProfile(UUID userId, String email, UpdateProfileRequest request) {
        UserProfile profile = findOrCreate(userId, email);
        userProfileMapper.updateFromRequest(request, profile);
        return userProfileMapper.toResponse(userProfileRepository.saveAndFlush(profile));
    }

    @Override
    @Transactional
    public void createProfileFromRegistration(UserRegisteredEvent event) {
        userProfileRepository.findById(event.userId()).ifPresentOrElse(
                existing -> {
                    if (existing.getFirstName() == null && existing.getLastName() == null) {
                        existing.setFirstName(event.firstName());
                        existing.setLastName(event.lastName());
                        log.info("Completed lazily created profile {} from registration event", existing.getId());
                    } else {
                        log.debug("Profile {} already exists, registration event ignored", existing.getId());
                    }
                },
                () -> {
                    userProfileRepository.save(UserProfile.builder()
                            .id(event.userId())
                            .email(normalize(event.email()))
                            .firstName(event.firstName())
                            .lastName(event.lastName())
                            .build());
                    log.info("Created profile {} from registration event", event.userId());
                });
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserProfileResponse> searchProfiles(String keyword, Pageable pageable) {
        Page<UserProfile> page = StringUtils.hasText(keyword)
                ? userProfileRepository.search(keyword.trim(), pageable)
                : userProfileRepository.findAll(pageable);
        return PageResponse.from(page, userProfileMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(UUID userId) {
        return userProfileRepository.findById(userId)
                .map(userProfileMapper::toResponse)
                .orElseThrow(() -> ResourceNotFoundException.of(PROFILE_NOT_FOUND));
    }

    private UserProfile findOrCreate(UUID userId, String email) {
        return userProfileRepository.findById(userId).orElseGet(() -> {
            log.info("Creating missing profile {} on first access", userId);
            return userProfileRepository.saveAndFlush(UserProfile.builder()
                    .id(userId)
                    .email(normalize(email))
                    .build());
        });
    }

    private String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
