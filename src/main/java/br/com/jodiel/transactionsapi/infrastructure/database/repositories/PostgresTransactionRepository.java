package br.com.jodiel.transactionsapi.infrastructure.database.repositories;

import br.com.jodiel.transactionsapi.domain.entities.Transaction;
import br.com.jodiel.transactionsapi.domain.enums.TransactionType;
import br.com.jodiel.transactionsapi.domain.interfaces.TransactionRepository;
import br.com.jodiel.transactionsapi.infrastructure.database.Uuids;
import br.com.jodiel.transactionsapi.infrastructure.database.jpa.TransactionJpaEntity;
import br.com.jodiel.transactionsapi.infrastructure.database.jparepositories.TransactionJpaRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class PostgresTransactionRepository implements TransactionRepository {

    private final TransactionJpaRepository jpa;
    private final EntityManager entityManager;

    public PostgresTransactionRepository(TransactionJpaRepository jpa, EntityManager entityManager) {
        this.jpa = jpa;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public String create(Transaction transaction) {
        TransactionJpaEntity entity = new TransactionJpaEntity();
        entity.setId(UUID.randomUUID());
        entity.setUserId(Uuids.require(transaction.getUserId()));
        entity.setRecipientId(transaction.getRecipientId() == null
                ? null
                : Uuids.require(transaction.getRecipientId()));
        entity.setType(transaction.getType().getValue());
        entity.setAmount(transaction.getAmount());
        entity.setCreatedAt(LocalDateTime.now());
        entity.setNew(true);

        return jpa.save(entity).getId().toString();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransactionListItem> listByUser(String userId, TransactionType type,
                                                LocalDateTime from, LocalDateTime to) {
        UUID id = Uuids.parse(userId).orElse(null);
        if (id == null) return List.of();

        StringBuilder sql = new StringBuilder("""
                SELECT t.id, t.type, t.amount, t.created_at,
                       t.user_id AS sender_id, sender.name AS sender_name,
                       t.recipient_id, recipient.name AS recipient_name
                FROM transactions t
                JOIN users sender ON sender.id = t.user_id
                LEFT JOIN users recipient ON recipient.id = t.recipient_id
                WHERE (t.user_id = :userId OR t.recipient_id = :userId)
                """);

        if (type != null) sql.append(" AND t.type = :type");
        if (from != null) sql.append(" AND t.created_at >= :from");
        if (to != null) sql.append(" AND t.created_at <= :to");
        sql.append(" ORDER BY t.created_at DESC");

        Query query = entityManager.createNativeQuery(sql.toString());
        query.setParameter("userId", id);
        if (type != null) query.setParameter("type", type.getValue());
        if (from != null) query.setParameter("from", from);
        if (to != null) query.setParameter("to", to);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        return rows.stream().map(row -> new TransactionListItem(
                asString(row[0]),
                TransactionType.valueOf(((String) row[1]).toUpperCase()),
                ((Number) row[2]).longValue(),
                asString(row[4]),
                (String) row[5],
                asString(row[6]),
                (String) row[7],
                asDateTime(row[3])
        )).toList();
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private static LocalDateTime asDateTime(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDateTime ldt) return ldt;
        if (value instanceof Timestamp ts) return ts.toLocalDateTime();
        throw new IllegalStateException("Unexpected created_at type: " + value.getClass());
    }
}
