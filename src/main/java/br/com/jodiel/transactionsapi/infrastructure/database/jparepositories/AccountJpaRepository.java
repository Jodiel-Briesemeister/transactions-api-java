package br.com.jodiel.transactionsapi.infrastructure.database.jparepositories;

import br.com.jodiel.transactionsapi.infrastructure.database.jpa.AccountJpaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AccountJpaRepository extends JpaRepository<AccountJpaEntity, UUID> {

    Optional<AccountJpaEntity> findByUserId(UUID userId);

    /**
     * Takes a row-level write lock (SELECT ... FOR UPDATE) so that concurrent debits on the same
     * account are serialized. Without it, two withdrawals can both pass the balance check and
     * drive the account negative.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM AccountJpaEntity a WHERE a.userId = :userId")
    Optional<AccountJpaEntity> findByUserIdForUpdate(@Param("userId") UUID userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE AccountJpaEntity a SET a.balance = a.balance + :delta, a.updatedAt = CURRENT_TIMESTAMP WHERE a.userId = :userId")
    int updateBalance(@Param("userId") UUID userId, @Param("delta") long delta);
}
