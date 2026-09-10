package br.com.jodiel.transactionsapi.application.usecases.transaction;

import br.com.jodiel.transactionsapi.domain.entities.Account;
import br.com.jodiel.transactionsapi.domain.entities.Transaction;
import br.com.jodiel.transactionsapi.domain.entities.User;
import br.com.jodiel.transactionsapi.domain.enums.NotificationTemplate;
import br.com.jodiel.transactionsapi.domain.enums.Queue;
import br.com.jodiel.transactionsapi.domain.enums.TransactionType;
import br.com.jodiel.transactionsapi.domain.errors.AppException;
import br.com.jodiel.transactionsapi.domain.interfaces.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class WithdrawUseCase {

    private static final Logger log = LoggerFactory.getLogger(WithdrawUseCase.class);

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final MessagePublisher messagePublisher;

    public WithdrawUseCase(AccountRepository accountRepository, TransactionRepository transactionRepository,
                           UserRepository userRepository, MessagePublisher messagePublisher) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
        this.messagePublisher = messagePublisher;
    }

    @Transactional
    public void execute(String userId, long amount) {
        if (amount <= 0) throw new AppException("Amount must be greater than zero", 422);

        // Locked read: the balance check and the debit must be atomic, or two concurrent
        // withdrawals both pass the check and drive the account negative.
        Account account = accountRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> {
                    log.error("Account not found userId={}", userId);
                    return new AppException("Account not found", 404);
                });

        if (account.getBalance() < amount) {
            throw new AppException("Insufficient balance", 422);
        }

        Transaction transaction = Transaction.create(userId, TransactionType.WITHDRAW, amount, null);
        transactionRepository.create(transaction);
        accountRepository.updateBalance(userId, -amount);

        log.info("Withdraw completed userId={} amount={}", userId, amount);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException("User not found", 404));

        messagePublisher.publish(Queue.NOTIFICATIONS_EMAIL, Map.of(
                "templateId", NotificationTemplate.TRANSACTION_WITHDRAW.getValue(),
                "userName", user.getName(),
                "userEmail", user.getEmail(),
                "amount", amount
        ));
    }
}
