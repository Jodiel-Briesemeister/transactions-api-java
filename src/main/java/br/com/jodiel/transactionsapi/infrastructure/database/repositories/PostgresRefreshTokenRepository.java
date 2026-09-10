package br.com.jodiel.transactionsapi.infrastructure.database.repositories;

import br.com.jodiel.transactionsapi.domain.interfaces.RefreshTokenRepository;
import br.com.jodiel.transactionsapi.infrastructure.database.Uuids;
import br.com.jodiel.transactionsapi.infrastructure.database.jpa.RefreshTokenJpaEntity;
import br.com.jodiel.transactionsapi.infrastructure.database.jparepositories.RefreshTokenJpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class PostgresRefreshTokenRepository implements RefreshTokenRepository {

    private final RefreshTokenJpaRepository jpa;

    public PostgresRefreshTokenRepository(RefreshTokenJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public void create(String userId, String token, LocalDateTime expiresAt) {
        RefreshTokenJpaEntity entity = new RefreshTokenJpaEntity();
        entity.setId(UUID.randomUUID());
        entity.setUserId(Uuids.require(userId));
        entity.setTokenHash(hash(token));
        entity.setExpiresAt(expiresAt);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setNew(true);

        jpa.save(entity);
    }

    @Override
    public Optional<RefreshTokenData> findByToken(String token) {
        return jpa.findByTokenHash(hash(token))
                .map(e -> new RefreshTokenData(e.getId().toString(), e.getUserId().toString(), e.getExpiresAt()));
    }

    @Override
    @Transactional
    public void deleteByToken(String token) {
        jpa.deleteByTokenHash(hash(token));
    }

    @Override
    @Transactional
    public void deleteAllByUser(String userId) {
        Uuids.parse(userId).ifPresent(jpa::deleteByUserId);
    }

    @Override
    @Transactional
    public void deleteExpired() {
        jpa.deleteExpiredBefore(LocalDateTime.now());
    }

    /**
     * Only the digest is stored, so a database leak does not hand out usable refresh tokens.
     * The token itself is a random UUID, so a plain SHA-256 (no salt/stretching) is enough here.
     */
    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
