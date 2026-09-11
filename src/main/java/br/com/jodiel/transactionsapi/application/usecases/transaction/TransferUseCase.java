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
public class TransferUseCase {

    private static final Logger log = LoggerFactory.getLogger(TransferUseCase.class);

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final MessagePublisher messagePublisher;

    public TransferUseCase(UserRepository userRepository, AccountRepository accountRepository,
                           TransactionRepository transactionRepository, MessagePublisher messagePublisher) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.messagePublisher = messagePublisher;
    }

    @Transactional
    public void execute(String userId, String recipientEmail, long amount) {
        if (amount <= 0) throw new AppException("Amount must be greater than zero", 422);

        // Unlocked existence check first, so a sender with no account still gets the right error.
        if (accountRepository.findByUserId(userId).isEmpty()) {
            log.error("Account not found userId={}", userId);
            throw new AppException("Account not found", 404);
        }

        User recipient = userRepository.findByEmail(recipientEmail)
                .orElseThrow(() -> new AppException("Recipient not found", 404));

        if (!recipient.isActive()) throw new AppException("Recipient account is inactive", 422);
        if (recipient.getId().equals(userId)) throw new AppException("Cannot transfer to yourself", 422);

        Account senderAccount = lockBothAccounts(userId, recipient.getId());

        if (senderAccount.getBalance() < amount) throw new AppException("Insufficient balance", 422);

        Transaction transaction = Transaction.create(userId, TransactionType.TRANSFER, amount, recipient.getId());
        transactionRepository.create(transaction);
        accountRepository.updateBalance(userId, -amount);
        accountRepository.updateBalance(recipient.getId(), amount);

        log.info("Transfer completed userId={} recipientId={} amount={}", userId, recipient.getId(), amount);

        User sender = userRepository.findById(userId)
                .orElseThrow(() -> new AppException("User not found", 404));

        messagePublisher.publish(Queue.NOTIFICATIONS_EMAIL, Map.of(
                "templateId", NotificationTemplate.TRANSACTION_TRANSFER_SENT.getValue(),
                "userName", sender.getName(),
                "userEmail", sender.getEmail(),
                "recipientName", recipient.getName(),
                "amount", amount
        ));
        messagePublisher.publish(Queue.NOTIFICATIONS_EMAIL, Map.of(
                "templateId", NotificationTemplate.TRANSACTION_TRANSFER_RECEIVED.getValue(),
                "userName", recipient.getName(),
                "userEmail", recipient.getEmail(),
                "senderName", sender.getName(),
                "amount", amount
        ));
    }

    /**
     * Takes a write lock on both accounts before the balance is read, so two concurrent transfers
     * cannot both see the same balance and overdraw it. The locks are always acquired in the same
     * id order: A→B and B→A running at once would otherwise deadlock on each other.
     *
     * @return the sender account, read under the lock
     */
    private Account lockBothAccounts(String senderId, String recipientId) {
        if (senderId.compareTo(recipientId) < 0) {
            Account sender = lock(senderId);
            lock(recipientId);
            return sender;
        }
        lock(recipientId);
        return lock(senderId);
    }

    private Account lock(String userId) {
        return accountRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> {
                    log.error("Account not found while locking userId={}", userId);
                    return new AppException("Account not found", 404);
                });
    }
}
