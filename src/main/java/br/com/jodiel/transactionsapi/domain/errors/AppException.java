package br.com.jodiel.transactionsapi.domain.errors;

import lombok.Getter;

@Getter
public class AppException extends RuntimeException {

    private final int statusCode;
    private final String code;

    public AppException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
        this.code = null;
    }

    public AppException(String message, int statusCode, String code) {
        super(message);
        this.statusCode = statusCode;
        this.code = code;
    }
}
