package br.com.jodiel.transactionsapi.domain.interfaces;

import java.time.LocalDateTime;
import java.util.Optional;

public interface RefreshTokenRepository {
    void create(String userId, String token, LocalDateTime expiresAt);
    Optional<RefreshTokenData> findByToken(String token);
    void deleteByToken(String token);
    void deleteAllByUser(String userId);
    void deleteExpired();

    record RefreshTokenData(String id, String userId, LocalDateTime expiresAt) {}
}
