package br.com.jodiel.transactionsapi.support;

import br.com.jodiel.transactionsapi.domain.entities.Account;
import br.com.jodiel.transactionsapi.domain.entities.User;

import java.time.LocalDateTime;

/** Shared builders so each test only spells out the field it actually cares about. */
public final class Fixtures {

    public static final String USER_ID = "11111111-1111-4111-8111-111111111111";
    public static final String RECIPIENT_ID = "22222222-2222-4222-8222-222222222222";

    private Fixtures() {}

    public static User user() {
        return user(USER_ID, "john@example.com", true);
    }

    public static User user(String id, String email, boolean active) {
        LocalDateTime now = LocalDateTime.now();
        return User.reconstitute(id, "John Doe", email, "hashed-password",
                "+5511999999999", active, now, now);
    }

    public static User recipient() {
        return User.reconstitute(RECIPIENT_ID, "Jane Doe", "jane@example.com", "hashed-password",
                null, true, LocalDateTime.now(), LocalDateTime.now());
    }

    public static Account account(String userId, long balance) {
        return Account.reconstitute("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", userId, balance,
                LocalDateTime.now());
    }
}
