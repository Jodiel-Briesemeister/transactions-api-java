package br.com.jodiel.transactionsapi.domain.interfaces;

import br.com.jodiel.transactionsapi.domain.entities.User;

import java.util.Optional;

public interface UserRepository {
    String create(User user);
    Optional<User> findByEmail(String email);
    Optional<User> findById(String id);
    User update(String id, String name, String email, String phone);
    void deactivate(String id);
    void reactivate(String id);
}
