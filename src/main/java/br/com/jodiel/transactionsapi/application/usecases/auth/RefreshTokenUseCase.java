package br.com.jodiel.transactionsapi.application.usecases.auth;

import br.com.jodiel.transactionsapi.application.dtos.auth.TokenResponse;
import br.com.jodiel.transactionsapi.domain.errors.AppException;
import br.com.jodiel.transactionsapi.domain.interfaces.AuthService;
import br.com.jodiel.transactionsapi.domain.interfaces.RefreshTokenRepository;
import br.com.jodiel.transactionsapi.domain.interfaces.RefreshTokenRepository.RefreshTokenData;
import br.com.jodiel.transactionsapi.domain.interfaces.RefreshTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

@Service
public class RefreshTokenUseCase {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenUseCase.class);

    private final AuthService authService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenService refreshTokenService;

    public RefreshTokenUseCase(AuthService authService,
                                RefreshTokenRepository refreshTokenRepository,
                                RefreshTokenService refreshTokenService) {
        this.authService = authService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public TokenResponse execute(String refreshToken) {
        RefreshTokenData stored = refreshTokenRepository.findByToken(refreshToken)
                .orElseThrow(() -> {
                    log.warn("Invalid refresh token used");
                    return new AppException("Invalid refresh token", 401);
                });

        if (LocalDateTime.now().isAfter(stored.expiresAt())) {
            log.warn("Expired refresh token used userId={}", stored.userId());
            throw new AppException("Expired refresh token", 401);
        }

        refreshTokenRepository.deleteByToken(refreshToken);

        String newAccessToken = authService.generateAccessToken(stored.userId());
        String newRefreshToken = refreshTokenService.createForUser(stored.userId());

        return new TokenResponse(newAccessToken, newRefreshToken);
    }
}
