package br.com.jodiel.transactionsapi.application.usecases.transaction;

import br.com.jodiel.transactionsapi.domain.entities.User;
import br.com.jodiel.transactionsapi.domain.errors.AppException;
import br.com.jodiel.transactionsapi.domain.interfaces.AccountRepository;
import br.com.jodiel.transactionsapi.domain.interfaces.UserRepository;
import br.com.jodiel.transactionsapi.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises {@code SELECT ... FOR UPDATE} against real rows; the mock-based unit tests only check
 * that the locking method is called.
 */
class ConcurrencyIntegrationTest extends AbstractIntegrationTest {

    private static final int THREADS = 10;

    @Autowired private WithdrawUseCase withdrawUseCase;
    @Autowired private TransferUseCase transferUseCase;
    @Autowired private DepositUseCase depositUseCase;
    @Autowired private UserRepository userRepository;
    @Autowired private AccountRepository accountRepository;

    private String createFundedUser(long balance) {
        String email = "concurrency-" + UUID.randomUUID() + "@example.com";
        String id = userRepository.create(
                User.create("Concurrency User", email, "hashed-password", null));
        accountRepository.create(id);
        if (balance > 0) depositUseCase.execute(id, balance);
        return id;
    }

    private long balanceOf(String userId) {
        return accountRepository.findByUserId(userId).orElseThrow().getBalance();
    }

    /**
     * Fires every task at the same instant so they collide inside the read-check-write window
     * instead of running one after another.
     */
    // Not try-with-resources: close() waits with no timeout, so a stuck task would hang the build
    // instead of failing the assertion below.
    @SuppressWarnings("resource")
    private void runConcurrently(Callable<?> task) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch startGate = new CountDownLatch(1);
        try {
            for (int i = 0; i < THREADS; i++) {
                pool.submit(() -> {
                    startGate.await();
                    return task.call();
                });
            }
            startGate.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(60, TimeUnit.SECONDS))
                    .as("all tasks finished before the timeout")
                    .isTrue();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("10 simultaneous withdrawals of 100 against a balance of 500: exactly 5 succeed")
    void concurrentWithdrawalsCannotOverdraw() throws Exception {
        String userId = createFundedUser(500L);

        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger refused = new AtomicInteger();
        List<Throwable> unexpected = new CopyOnWriteArrayList<>();

        runConcurrently(() -> {
            try {
                withdrawUseCase.execute(userId, 100L);
                succeeded.incrementAndGet();
            } catch (AppException e) {
                if ("Insufficient balance".equals(e.getMessage())) {
                    refused.incrementAndGet();
                } else {
                    unexpected.add(e);
                }
            } catch (Throwable t) {
                unexpected.add(t);
            }
            return null;
        });

        assertThat(unexpected).as("no unexpected failures").isEmpty();
        assertThat(succeeded.get()).as("withdrawals allowed").isEqualTo(5);
        assertThat(refused.get()).as("withdrawals refused").isEqualTo(5);
        assertThat(balanceOf(userId)).as("final balance is never negative").isZero();
    }

    @Test
    @DisplayName("transfers in both directions at once neither deadlock nor create money")
    void opposingTransfersDoNotDeadlock() throws Exception {
        String alice = createFundedUser(1_000L);
        String bob = createFundedUser(1_000L);

        String aliceEmail = userRepository.findById(alice).orElseThrow().getEmail();
        String bobEmail = userRepository.findById(bob).orElseThrow().getEmail();

        AtomicInteger completed = new AtomicInteger();
        List<Throwable> failures = new CopyOnWriteArrayList<>();
        AtomicInteger index = new AtomicInteger();

        // Half the tasks send Alice -> Bob, half send Bob -> Alice. Locking the accounts in the
        // order they are named would let two transactions each hold the row the other needs.
        runConcurrently(() -> {
            boolean aliceToBob = index.getAndIncrement() % 2 == 0;
            try {
                if (aliceToBob) {
                    transferUseCase.execute(alice, bobEmail, 10L);
                } else {
                    transferUseCase.execute(bob, aliceEmail, 10L);
                }
                completed.incrementAndGet();
            } catch (Throwable t) {
                failures.add(t);
            }
            return null;
        });

        assertThat(failures).as("no deadlock or other failure").isEmpty();
        assertThat(completed.get()).isEqualTo(10);

        // Money is only ever moved, never created or destroyed.
        assertThat(balanceOf(alice) + balanceOf(bob)).isEqualTo(2_000L);
        assertThat(balanceOf(alice)).isNotNegative();
        assertThat(balanceOf(bob)).isNotNegative();
    }
}
