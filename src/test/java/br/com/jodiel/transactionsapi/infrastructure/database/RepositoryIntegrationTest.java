package br.com.jodiel.transactionsapi.infrastructure.database;

import br.com.jodiel.transactionsapi.domain.entities.Account;
import br.com.jodiel.transactionsapi.domain.entities.Transaction;
import br.com.jodiel.transactionsapi.domain.entities.User;
import br.com.jodiel.transactionsapi.domain.enums.TransactionType;
import br.com.jodiel.transactionsapi.domain.interfaces.AccountRepository;
import br.com.jodiel.transactionsapi.domain.interfaces.RefreshTokenRepository;
import br.com.jodiel.transactionsapi.domain.interfaces.TransactionRepository;
import br.com.jodiel.transactionsapi.domain.interfaces.UserRepository;
import br.com.jodiel.transactionsapi.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the repositories against a real Postgres. These are the tests that would have caught
 * the original String-to-uuid mapping bug: every statement here binds a uuid parameter.
 */
class RepositoryIntegrationTest extends AbstractIntegrationTest {

    private static final AtomicInteger COUNTER = new AtomicInteger();

    @Autowired private UserRepository userRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;

    private String uniqueEmail() {
        return "user" + COUNTER.incrementAndGet() + "-" + UUID.randomUUID() + "@example.com";
    }

    private String createUser(String email) {
        return userRepository.create(User.create("Test User", email, "hashed-password", "+5511999999999"));
    }

    @Test
    @DisplayName("persists a user and reads it back by id and by email")
    void persistsAndReadsUser() {
        String email = uniqueEmail();
        String id = createUser(email);

        assertThat(id).isNotNull();
        assertThat(UUID.fromString(id)).isNotNull();

        Optional<User> byId = userRepository.findById(id);
        assertThat(byId).isPresent();
        assertThat(byId.get().getEmail()).isEqualTo(email);
        assertThat(byId.get().isActive()).isTrue();

        Optional<User> byEmail = userRepository.findByEmail(email);
        assertThat(byEmail).isPresent();
        assertThat(byEmail.get().getId()).isEqualTo(id);
        // findByEmail is the credential path, so the hash must survive the round trip.
        assertThat(byEmail.get().getPasswordHash()).isEqualTo("hashed-password");
    }

    @Test
    @DisplayName("returns empty instead of blowing up when the id is not a uuid")
    void toleratesMalformedId() {
        assertThat(userRepository.findById("not-a-uuid")).isEmpty();
        assertThat(accountRepository.findByUserId("not-a-uuid")).isEmpty();
        assertThat(transactionRepository.listByUser("not-a-uuid", null, null, null)).isEmpty();
    }

    @Test
    @DisplayName("updates, deactivates and reactivates a user, keeping the cache in step")
    void updatesUserLifecycle() {
        String id = createUser(uniqueEmail());
        String newEmail = uniqueEmail();

        User updated = userRepository.update(id, "Renamed", newEmail, "+5511777777777");
        assertThat(updated.getName()).isEqualTo("Renamed");
        assertThat(updated.getEmail()).isEqualTo(newEmail);

        // Reads go through the Redis-backed decorator; a stale entry would show the old name.
        assertThat(userRepository.findById(id)).get()
                .extracting(User::getName).isEqualTo("Renamed");

        userRepository.deactivate(id);
        assertThat(userRepository.findById(id)).get().extracting(User::isActive).isEqualTo(false);

        userRepository.reactivate(id);
        assertThat(userRepository.findById(id)).get().extracting(User::isActive).isEqualTo(true);
    }

    @Test
    @DisplayName("opens an account with a zero balance and applies signed deltas")
    void movesAccountBalance() {
        String id = createUser(uniqueEmail());
        accountRepository.create(id);

        assertThat(accountRepository.findByUserId(id)).get()
                .extracting(Account::getBalance).isEqualTo(0L);

        accountRepository.updateBalance(id, 500L);
        accountRepository.updateBalance(id, -200L);

        assertThat(accountRepository.findByUserId(id)).get()
                .extracting(Account::getBalance).isEqualTo(300L);
    }

    @Test
    @Transactional
    @DisplayName("takes a row lock without error (SELECT ... FOR UPDATE needs a transaction)")
    void locksAccountRow() {
        String id = createUser(uniqueEmail());
        accountRepository.create(id);

        assertThat(accountRepository.findByUserIdForUpdate(id)).isPresent();
    }

    @Test
    @DisplayName("lists transactions for both sides of a transfer and honours the filters")
    void listsTransactions() {
        String senderId = createUser(uniqueEmail());
        String recipientId = createUser(uniqueEmail());

        transactionRepository.create(
                Transaction.create(senderId, TransactionType.DEPOSIT, 1_000L, null));
        transactionRepository.create(
                Transaction.create(senderId, TransactionType.TRANSFER, 250L, recipientId));

        List<TransactionRepository.TransactionListItem> senderRows =
                transactionRepository.listByUser(senderId, null, null, null);
        assertThat(senderRows).hasSize(2);

        // The recipient sees the transfer even though the row belongs to the sender.
        List<TransactionRepository.TransactionListItem> recipientRows =
                transactionRepository.listByUser(recipientId, null, null, null);
        assertThat(recipientRows).hasSize(1);
        assertThat(recipientRows.getFirst().type()).isEqualTo(TransactionType.TRANSFER);
        assertThat(recipientRows.getFirst().recipientId()).isEqualTo(recipientId);
        assertThat(recipientRows.getFirst().senderId()).isEqualTo(senderId);

        List<TransactionRepository.TransactionListItem> deposits =
                transactionRepository.listByUser(senderId, TransactionType.DEPOSIT, null, null);
        assertThat(deposits).hasSize(1);
        assertThat(deposits.getFirst().amount()).isEqualTo(1_000L);

        List<TransactionRepository.TransactionListItem> future = transactionRepository.listByUser(
                senderId, null, LocalDateTime.now().plusDays(1), null);
        assertThat(future).isEmpty();
    }

    @Test
    @DisplayName("stores refresh tokens hashed and deletes them by value, by user and by expiry")
    void managesRefreshTokens() {
        String id = createUser(uniqueEmail());
        String token = UUID.randomUUID().toString();

        refreshTokenRepository.create(id, token, LocalDateTime.now().plusDays(7));

        assertThat(refreshTokenRepository.findByToken(token)).isPresent();
        assertThat(refreshTokenRepository.findByToken(UUID.randomUUID().toString())).isEmpty();

        refreshTokenRepository.deleteByToken(token);
        assertThat(refreshTokenRepository.findByToken(token)).isEmpty();

        String second = UUID.randomUUID().toString();
        refreshTokenRepository.create(id, second, LocalDateTime.now().plusDays(7));
        refreshTokenRepository.deleteAllByUser(id);
        assertThat(refreshTokenRepository.findByToken(second)).isEmpty();

        String expired = UUID.randomUUID().toString();
        refreshTokenRepository.create(id, expired, LocalDateTime.now().minusDays(1));
        refreshTokenRepository.deleteExpired();
        assertThat(refreshTokenRepository.findByToken(expired)).isEmpty();
    }
}
