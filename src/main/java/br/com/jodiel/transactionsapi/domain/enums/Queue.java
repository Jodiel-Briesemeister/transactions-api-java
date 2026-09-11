package br.com.jodiel.transactionsapi.domain.enums;

import java.util.Locale;

public enum Queue {
    NOTIFICATIONS_EMAIL;

    public String getName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
