package br.com.jodiel.transactionsapi.infrastructure.services;

import br.com.jodiel.transactionsapi.domain.interfaces.CacheService;
import br.com.jodiel.transactionsapi.domain.interfaces.TokenBlacklistService;
import org.springframework.stereotype.Service;

@Service
public class RedisTokenBlacklistService implements TokenBlacklistService {

    private final CacheService cacheService;

    public RedisTokenBlacklistService(CacheService cacheService) {
        this.cacheService = cacheService;
    }

    @Override
    public void add(String token, long ttlSeconds) {
        cacheService.set("blacklist", token, "true", ttlSeconds);
    }

    @Override
    public boolean has(String token) {
        return cacheService.get("blacklist", token).isPresent();
    }
}
