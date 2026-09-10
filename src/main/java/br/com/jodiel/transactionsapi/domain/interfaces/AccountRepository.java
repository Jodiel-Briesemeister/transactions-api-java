package br.com.jodiel.transactionsapi.domain.interfaces;

import br.com.jodiel.transactionsapi.domain.entities.Account;

import java.util.Optional;

public interface AccountRepository {
    void create(String userId);

    Optional<Account> findByUserId(String userId);

    Optional<Account> findByUserIdForUpdate(String userId);

    void updateBalance(String userId, long delta);
}
