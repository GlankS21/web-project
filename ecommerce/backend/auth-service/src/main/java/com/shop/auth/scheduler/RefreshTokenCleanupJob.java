package com.shop.auth.scheduler;

import com.shop.auth.service.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Removes expired refresh tokens. Safe to run on several instances at once (idempotent delete).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenCleanupJob {

    private final TokenService tokenService;

    @Scheduled(cron = "${app.auth.refresh-token-cleanup-cron:0 0 3 * * *}")
    public void purgeExpiredTokens() {
        int deleted = tokenService.purgeExpired();
        log.info("Refresh token cleanup removed {} expired token(s)", deleted);
    }
}
