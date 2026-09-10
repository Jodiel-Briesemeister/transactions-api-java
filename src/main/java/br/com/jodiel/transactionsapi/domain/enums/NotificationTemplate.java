package br.com.jodiel.transactionsapi.domain.enums;

public enum NotificationTemplate {
    USER_REGISTERED("user_registered"),
    USER_DEACTIVATED("user_deactivated"),
    USER_REACTIVATED("user_reactivated"),
    TRANSACTION_DEPOSIT("transaction_deposit"),
    TRANSACTION_WITHDRAW("transaction_withdraw"),
    TRANSACTION_TRANSFER_SENT("transaction_transfer_sent"),
    TRANSACTION_TRANSFER_RECEIVED("transaction_transfer_received");

    private final String value;

    NotificationTemplate(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
