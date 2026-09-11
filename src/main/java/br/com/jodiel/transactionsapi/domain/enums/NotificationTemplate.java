package br.com.jodiel.transactionsapi.domain.enums;

import java.util.Locale;

public enum NotificationTemplate {
    USER_REGISTERED,
    USER_DEACTIVATED,
    USER_REACTIVATED,
    TRANSACTION_DEPOSIT,
    TRANSACTION_WITHDRAW,
    TRANSACTION_TRANSFER_SENT,
    TRANSACTION_TRANSFER_RECEIVED;

    public String getValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
