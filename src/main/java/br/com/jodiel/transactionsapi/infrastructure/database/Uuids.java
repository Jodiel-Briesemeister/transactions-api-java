package br.com.jodiel.transactionsapi.infrastructure.database;

import java.util.Optional;
import java.util.UUID;

/**
 * The domain layer speaks in opaque String ids; Postgres columns are real uuid. Conversion happens
 * here, at the infrastructure boundary, so a malformed id (e.g. from a forged token) becomes an
 * empty lookup instead of a 500.
 */
public final class Uuids {

    private Uuids() {}

    public static Optional<UUID> parse(String value) {
        if (value == null || value.isBlank()) return Optional.empty();
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public static UUID require(String value) {
        return parse(value).orElseThrow(
                () -> new IllegalArgumentException("Not a valid UUID: " + value));
    }

    public static String toStringOrNull(UUID value) {
        return value == null ? null : value.toString();
    }
}
