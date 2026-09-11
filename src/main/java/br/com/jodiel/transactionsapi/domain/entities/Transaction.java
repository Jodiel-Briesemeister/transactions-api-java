package br.com.jodiel.transactionsapi.domain.entities;

import br.com.jodiel.transactionsapi.domain.enums.TransactionType;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class Transaction {

    private final String userId;
    private final TransactionType type;
    private final long amount;
    private final String recipientId;
    private final LocalDateTime createdAt;

    private Transaction(String userId, TransactionType type,
                        long amount, String recipientId, LocalDateTime createdAt) {
        this.userId = userId;
        this.type = type;
        this.amount = amount;
        this.recipientId = recipientId;
        this.createdAt = createdAt;
    }

    public static Transaction create(String userId, TransactionType type, long amount, String recipientId) {
        return new Transaction(userId, type, amount, recipientId, LocalDateTime.now());
    }
}
