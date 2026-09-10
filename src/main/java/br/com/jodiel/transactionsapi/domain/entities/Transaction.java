package br.com.jodiel.transactionsapi.domain.entities;

import br.com.jodiel.transactionsapi.domain.enums.TransactionType;

import java.time.LocalDateTime;

public class Transaction {

    private final String id;
    private final String userId;
    private final TransactionType type;
    private final long amount;
    private final String recipientId;
    private final LocalDateTime createdAt;

    private Transaction(String id, String userId, TransactionType type,
                        long amount, String recipientId, LocalDateTime createdAt) {
        this.id = id;
        this.userId = userId;
        this.type = type;
        this.amount = amount;
        this.recipientId = recipientId;
        this.createdAt = createdAt;
    }

    public static Transaction create(String userId, TransactionType type, long amount, String recipientId) {
        return new Transaction(null, userId, type, amount, recipientId, LocalDateTime.now());
    }

    public static Transaction reconstitute(String id, String userId, TransactionType type,
                                            long amount, String recipientId, LocalDateTime createdAt) {
        return new Transaction(id, userId, type, amount, recipientId, createdAt);
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public TransactionType getType() { return type; }
    public long getAmount() { return amount; }
    public String getRecipientId() { return recipientId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
