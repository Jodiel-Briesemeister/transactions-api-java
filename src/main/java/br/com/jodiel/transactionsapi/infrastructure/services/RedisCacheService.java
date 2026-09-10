package br.com.jodiel.transactionsapi.infrastructure.services;

import br.com.jodiel.transactionsapi.domain.interfaces.CacheService;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class RedisCacheService implements CacheService {

    private static final long DEFAULT_TTL_SECONDS = 300;

    private final StringRedisTemplate redis;

    public RedisCacheService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Optional<String> get(String namespace, String key) {
        return Optional.ofNullable(redis.opsForValue().get(namespace + ":" + key));
    }

    @Override
    public void set(String namespace, String key, String value) {
        redis.opsForValue().set(namespace + ":" + key, value, Duration.ofSeconds(DEFAULT_TTL_SECONDS));
    }

    @Override
    public void set(String namespace, String key, String value, long ttlSeconds) {
        redis.opsForValue().set(namespace + ":" + key, value, Duration.ofSeconds(ttlSeconds));
    }

    @Override
    public void delete(String namespace, String key) {
        redis.delete(namespace + ":" + key);
    }
}
