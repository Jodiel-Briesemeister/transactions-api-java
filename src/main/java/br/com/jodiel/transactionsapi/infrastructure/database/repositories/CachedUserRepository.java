package br.com.jodiel.transactionsapi.infrastructure.database.repositories;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import br.com.jodiel.transactionsapi.domain.entities.User;
import br.com.jodiel.transactionsapi.domain.interfaces.CacheService;
import br.com.jodiel.transactionsapi.domain.interfaces.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;

/**
 * Decorator that caches {@link #findById} in Redis. Every write path evicts the entry, so a stale
 * profile is never served.
 */
@Repository
@Primary
public class CachedUserRepository implements UserRepository {

    private static final Logger log = LoggerFactory.getLogger(CachedUserRepository.class);
    private static final String NAMESPACE = "user";

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private final UserRepository delegate;
    private final CacheService cacheService;

    public CachedUserRepository(@Qualifier("postgresUserRepository") UserRepository delegate,
                                CacheService cacheService) {
        this.delegate = delegate;
        this.cacheService = cacheService;
    }

    @Override
    public String create(User user) {
        return delegate.create(user);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        // Not cached: this is the credential lookup path and needs the current password hash.
        return delegate.findByEmail(email);
    }

    @Override
    public Optional<User> findById(String id) {
        Optional<User> cached = readFromCache(id);
        if (cached.isPresent()) return cached;

        Optional<User> user = delegate.findById(id);
        user.ifPresent(u -> writeToCache(id, u));
        return user;
    }

    @Override
    public User update(String id, String name, String email, String phone) {
        User updated = delegate.update(id, name, email, phone);
        evict(id);
        return updated;
    }

    @Override
    public void deactivate(String id) {
        delegate.deactivate(id);
        evict(id);
    }

    @Override
    public void reactivate(String id) {
        delegate.reactivate(id);
        evict(id);
    }

    private Optional<User> readFromCache(String id) {
        try {
            return cacheService.get(NAMESPACE, id).flatMap(this::parse);
        } catch (RuntimeException e) {
            // A cache outage must not take the API down with it.
            log.warn("Cache read failed for user {}: {}", id, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<User> parse(String json) {
        try {
            return Optional.of(MAPPER.readValue(json, CachedUser.class).toUser());
        } catch (JsonProcessingException e) {
            log.warn("Discarding unreadable cached user entry: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private void writeToCache(String id, User user) {
        try {
            cacheService.set(NAMESPACE, id, MAPPER.writeValueAsString(CachedUser.from(user)));
        } catch (JsonProcessingException | RuntimeException e) {
            log.warn("Cache write failed for user {}: {}", id, e.getMessage());
        }
    }

    private void evict(String id) {
        try {
            cacheService.delete(NAMESPACE, id);
        } catch (RuntimeException e) {
            log.warn("Cache eviction failed for user {}: {}", id, e.getMessage());
        }
    }

    /**
     * Deliberately does not carry the password hash: nothing that reads a user by id needs it,
     * and keeping credentials out of Redis limits the blast radius of a cache leak.
     */
    record CachedUser(String id, String name, String email, String phone,
                      boolean isActive, String createdAt, String updatedAt) {

        static CachedUser from(User u) {
            return new CachedUser(u.getId(), u.getName(), u.getEmail(), u.getPhone(),
                    u.isActive(), u.getCreatedAt().toString(), u.getUpdatedAt().toString());
        }

        User toUser() {
            return User.reconstitute(id, name, email, null, phone, isActive,
                    LocalDateTime.parse(createdAt), LocalDateTime.parse(updatedAt));
        }
    }
}
