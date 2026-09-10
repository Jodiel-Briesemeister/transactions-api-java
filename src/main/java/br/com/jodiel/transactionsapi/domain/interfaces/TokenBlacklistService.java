package br.com.jodiel.transactionsapi.domain.interfaces;

public interface TokenBlacklistService {
    void add(String token, long ttlSeconds);
    boolean has(String token);
}
