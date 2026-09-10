package br.com.jodiel.transactionsapi.domain.interfaces;

public interface PasswordService {
    String hash(String password);
    boolean verify(String rawPassword, String hash);
}
