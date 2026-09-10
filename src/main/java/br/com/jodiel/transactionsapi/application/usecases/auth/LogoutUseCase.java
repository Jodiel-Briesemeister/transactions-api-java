package br.com.jodiel.transactionsapi.application.usecases.auth;

import br.com.jodiel.transactionsapi.domain.interfaces.AuthService;
import br.com.jodiel.transactionsapi.domain.interfaces.RefreshTokenRepository;
import br.com.jodiel.transactionsapi.domain.interfaces.TokenBlacklistService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

@Service
public class LogoutUseCase {

    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenBlacklistService tokenBlacklistService;
    private final AuthService authService;

    public LogoutUseCase(RefreshTokenRepository refreshTokenRepository,
                         TokenBlacklistService tokenBlacklistService,
                         AuthService authService) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.tokenBlacklistService = tokenBlacklistService;
        this.authService = authService;
    }

    @Transactional
    public void execute(String refreshToken, String accessToken) {
        refreshTokenRepository.deleteByToken(refreshToken);

        long ttl = authService.getTokenRemainingSeconds(accessToken);
        if (ttl > 0) {
            tokenBlacklistService.add(accessToken, ttl);
        }
    }
}
