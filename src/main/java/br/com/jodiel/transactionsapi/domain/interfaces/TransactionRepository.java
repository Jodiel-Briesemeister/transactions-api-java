package br.com.jodiel.transactionsapi.domain.interfaces;

import br.com.jodiel.transactionsapi.domain.entities.Transaction;
import br.com.jodiel.transactionsapi.domain.enums.TransactionType;

import java.time.LocalDateTime;
import java.util.List;

public interface TransactionRepository {
    String create(Transaction transaction);
    List<TransactionListItem> listByUser(String userId, TransactionType type,
                                         LocalDateTime from, LocalDateTime to);

    record TransactionListItem(
            String id,
            TransactionType type,
            long amount,
            String senderId,
            String senderName,
            String recipientId,
            String recipientName,
            LocalDateTime createdAt
    ) {}
}
