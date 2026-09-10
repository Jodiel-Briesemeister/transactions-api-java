package br.com.jodiel.transactionsapi.domain.interfaces;

public interface RefreshTokenService {
    String createForUser(String userId);
}
