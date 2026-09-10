package br.com.jodiel.transactionsapi.infrastructure.database.repositories;

import br.com.jodiel.transactionsapi.domain.entities.Account;
import br.com.jodiel.transactionsapi.domain.interfaces.AccountRepository;
import br.com.jodiel.transactionsapi.infrastructure.database.Uuids;
import br.com.jodiel.transactionsapi.infrastructure.database.jpa.AccountJpaEntity;
import br.com.jodiel.transactionsapi.infrastructure.database.jparepositories.AccountJpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class PostgresAccountRepository implements AccountRepository {

    private final AccountJpaRepository jpa;

    public PostgresAccountRepository(AccountJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public void create(String userId) {
        LocalDateTime now = LocalDateTime.now();

        AccountJpaEntity entity = new AccountJpaEntity();
        entity.setId(UUID.randomUUID());
        entity.setUserId(Uuids.require(userId));
        entity.setBalance(0L);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setNew(true);

        jpa.save(entity);
    }

    @Override
    public Optional<Account> findByUserId(String userId) {
        return Uuids.parse(userId).flatMap(jpa::findByUserId).map(this::toDomain);
    }

    @Override
    public Optional<Account> findByUserIdForUpdate(String userId) {
        return Uuids.parse(userId).flatMap(jpa::findByUserIdForUpdate).map(this::toDomain);
    }

    @Override
    @Transactional
    public void updateBalance(String userId, long delta) {
        jpa.updateBalance(Uuids.require(userId), delta);
    }

    private Account toDomain(AccountJpaEntity e) {
        return Account.reconstitute(e.getId().toString(), e.getUserId().toString(),
                e.getBalance(), e.getCreatedAt());
    }
}
