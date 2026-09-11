package br.com.jodiel.transactionsapi.domain.entities;

import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class Account {

    private final String id;
    private final String userId;
    private final long balance;
    private final LocalDateTime createdAt;

    private Account(String id, String userId, long balance, LocalDateTime createdAt) {
        this.id = id;
        this.userId = userId;
        this.balance = balance;
        this.createdAt = createdAt;
    }

    public static Account create(String userId) {
        return new Account(null, userId, 0L, LocalDateTime.now());
    }

    public static Account reconstitute(String id, String userId, long balance, LocalDateTime createdAt) {
        return new Account(id, userId, balance, createdAt);
    }
}
