package br.com.jodiel.transactionsapi.domain.enums;

import java.util.Locale;

public enum TransactionType {
    DEPOSIT,
    WITHDRAW,
    TRANSFER;

    public String getValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
