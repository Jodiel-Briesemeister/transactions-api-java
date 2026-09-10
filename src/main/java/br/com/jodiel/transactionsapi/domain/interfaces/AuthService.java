package br.com.jodiel.transactionsapi.domain.interfaces;

public interface AuthService {
    String generateAccessToken(String userId);
    String verifyAccessToken(String token);
    long getTokenRemainingSeconds(String token);
}
