package br.com.jodiel.transactionsapi.infrastructure.jobs;

import br.com.jodiel.transactionsapi.domain.interfaces.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CleanupExpiredTokensJob {

    private static final Logger log = LoggerFactory.getLogger(CleanupExpiredTokensJob.class);

    private final RefreshTokenRepository refreshTokenRepository;

    public CleanupExpiredTokensJob(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Scheduled(cron = "0 0 * * * *")
    public void run() {
        log.info("Running expired refresh tokens cleanup");
        refreshTokenRepository.deleteExpired();
        log.info("Expired refresh tokens cleanup done");
    }
}
