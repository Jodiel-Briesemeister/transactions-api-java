package br.com.jodiel.transactionsapi.domain.entities;

import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class User {

    private final String id;
    private final String name;
    private final String email;
    private final String passwordHash;
    private final String phone;
    private final boolean isActive;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    private User(String id, String name, String email, String passwordHash,
                 String phone, boolean isActive, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.phone = phone;
        this.isActive = isActive;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static User create(String name, String email, String passwordHash, String phone) {
        return new User(null, name, email, passwordHash, phone, true,
                LocalDateTime.now(), LocalDateTime.now());
    }

    public static User reconstitute(String id, String name, String email, String passwordHash,
                                     String phone, boolean isActive,
                                     LocalDateTime createdAt, LocalDateTime updatedAt) {
        return new User(id, name, email, passwordHash, phone, isActive, createdAt, updatedAt);
    }
}
