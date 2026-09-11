package br.com.jodiel.transactionsapi.infrastructure.database.repositories;

import br.com.jodiel.transactionsapi.domain.entities.User;
import br.com.jodiel.transactionsapi.domain.errors.AppException;
import br.com.jodiel.transactionsapi.domain.interfaces.UserRepository;
import br.com.jodiel.transactionsapi.infrastructure.database.Uuids;
import br.com.jodiel.transactionsapi.infrastructure.database.jpa.UserJpaEntity;
import br.com.jodiel.transactionsapi.infrastructure.database.jparepositories.UserJpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class PostgresUserRepository implements UserRepository {

    private final UserJpaRepository jpa;

    public PostgresUserRepository(UserJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public String create(User user) {
        UUID id = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        UserJpaEntity entity = new UserJpaEntity();
        entity.setId(id);
        entity.setName(user.getName());
        entity.setEmail(user.getEmail());
        entity.setPasswordHash(user.getPasswordHash());
        entity.setPhone(user.getPhone());
        entity.setActive(true);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setNew(true);

        jpa.save(entity);
        return id.toString();
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return jpa.findByEmail(email).map(this::toDomain);
    }

    @Override
    public Optional<User> findById(String id) {
        return Uuids.parse(id).flatMap(jpa::findById).map(this::toDomain);
    }

    @Override
    @Transactional
    public User update(String id, String name, String email, String phone) {
        UserJpaEntity entity = Uuids.parse(id)
                .flatMap(jpa::findById)
                .orElseThrow(() -> new AppException("User not found", 404));

        if (name != null) entity.setName(name);
        if (email != null) entity.setEmail(email);
        if (phone != null) entity.setPhone(phone);
        entity.setUpdatedAt(LocalDateTime.now());

        return toDomain(jpa.save(entity));
    }

    @Override
    @Transactional
    public void deactivate(String id) {
        Uuids.parse(id).ifPresent(jpa::deactivateById);
    }

    @Override
    @Transactional
    public void reactivate(String id) {
        Uuids.parse(id).ifPresent(jpa::reactivateById);
    }

    private User toDomain(UserJpaEntity e) {
        return User.reconstitute(Objects.requireNonNull(e.getId()).toString(), e.getName(), e.getEmail(),
                e.getPasswordHash(), e.getPhone(), e.isActive(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
