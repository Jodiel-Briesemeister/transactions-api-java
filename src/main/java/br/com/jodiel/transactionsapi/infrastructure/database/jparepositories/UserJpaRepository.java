package br.com.jodiel.transactionsapi.infrastructure.database.jparepositories;

import br.com.jodiel.transactionsapi.infrastructure.database.jpa.UserJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserJpaRepository extends JpaRepository<UserJpaEntity, UUID> {
    Optional<UserJpaEntity> findByEmail(String email);

    boolean existsByEmail(String email);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE UserJpaEntity u SET u.isActive = false, u.updatedAt = CURRENT_TIMESTAMP WHERE u.id = :id")
    void deactivateById(@Param("id") UUID id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE UserJpaEntity u SET u.isActive = true, u.updatedAt = CURRENT_TIMESTAMP WHERE u.id = :id")
    void reactivateById(@Param("id") UUID id);
}
