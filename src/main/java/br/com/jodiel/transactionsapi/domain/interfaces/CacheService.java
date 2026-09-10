package br.com.jodiel.transactionsapi.domain.interfaces;

import java.util.Optional;

public interface CacheService {
    Optional<String> get(String namespace, String key);
    void set(String namespace, String key, String value);
    void set(String namespace, String key, String value, long ttlSeconds);
    void delete(String namespace, String key);
}
