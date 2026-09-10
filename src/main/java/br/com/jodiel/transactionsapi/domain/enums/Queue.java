package br.com.jodiel.transactionsapi.domain.enums;

public enum Queue {
    NOTIFICATIONS_EMAIL("notifications_email");

    private final String name;

    Queue(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
