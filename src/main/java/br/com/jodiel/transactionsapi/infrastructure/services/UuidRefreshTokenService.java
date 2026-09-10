package br.com.jodiel.transactionsapi.infrastructure.services;

import br.com.jodiel.transactionsapi.domain.interfaces.RefreshTokenRepository;
import br.com.jodiel.transactionsapi.domain.interfaces.RefreshTokenService;

import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

@Service
public class UuidRefreshTokenService implements RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final long expiresInDays;

    public UuidRefreshTokenService(RefreshTokenRepository refreshTokenRepository,
                                   @Value("${app.jwt.refresh-token-expires-in-days}") long expiresInDays) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.expiresInDays = expiresInDays;
    }

    @Override
    public String createForUser(String userId) {
        String token = UUID.randomUUID().toString();
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(expiresInDays);
        refreshTokenRepository.create(userId, token, expiresAt);
        return token;
    }
}
