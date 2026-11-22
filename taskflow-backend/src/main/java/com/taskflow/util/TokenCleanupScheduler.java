package com.taskflow.util;

import com.taskflow.repository.ActivationTokenRepository;
import com.taskflow.repository.RefreshTokenRepository;
import com.taskflow.repository.RevokedTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
public class TokenCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(TokenCleanupScheduler.class);

    private final RevokedTokenRepository revokedTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final ActivationTokenRepository activationTokenRepository;

    public TokenCleanupScheduler(
            RevokedTokenRepository revokedTokenRepository,
            RefreshTokenRepository refreshTokenRepository,
            ActivationTokenRepository activationTokenRepository
    ) {
        this.revokedTokenRepository = revokedTokenRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.activationTokenRepository = activationTokenRepository;
    }

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void purgeExpiredTokens() {
        Instant now = Instant.now();
        int revokedDeleted = revokedTokenRepository.deleteExpiredBefore(now);
        int refreshDeleted = refreshTokenRepository.deleteExpiredBefore(now);
        int activationDeleted = activationTokenRepository.deleteExpiredBefore(now);
        if (revokedDeleted > 0 || refreshDeleted > 0 || activationDeleted > 0) {
            log.info("Purged {} revoked, {} refresh, {} activation tokens",
                    revokedDeleted, refreshDeleted, activationDeleted);
        }
    }
}
