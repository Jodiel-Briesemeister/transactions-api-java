package br.com.jodiel.transactionsapi.infrastructure.database.repositories;

import br.com.jodiel.transactionsapi.domain.entities.Transaction;
import br.com.jodiel.transactionsapi.domain.enums.TransactionType;
import br.com.jodiel.transactionsapi.domain.interfaces.TransactionRepository;
import br.com.jodiel.transactionsapi.infrastructure.database.Uuids;
import br.com.jodiel.transactionsapi.infrastructure.database.jpa.TransactionJpaEntity;
import br.com.jodiel.transactionsapi.infrastructure.database.jparepositories.TransactionJpaRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class PostgresTransactionRepository implements TransactionRepository {

    private final TransactionJpaRepository jpa;
    private final JdbcClient jdbcClient;

    public PostgresTransactionRepository(TransactionJpaRepository jpa, JdbcClient jdbcClient) {
        this.jpa = jpa;
        this.jdbcClient = jdbcClient;
    }

    @Override
    @Transactional
    public void create(Transaction transaction) {
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

        jpa.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransactionListItem> listByUser(String userId, TransactionType type,
                                                LocalDateTime from, LocalDateTime to) {
        UUID id = Uuids.parse(userId).orElse(null);
        if (id == null) return List.of();

        JdbcClient.StatementSpec statement = jdbcClient.sql(listByUserSql(type, from, to))
                .param("userId", id);
        if (type != null) statement.param("type", type.getValue());
        if (from != null) statement.param("from", from);
        if (to != null) statement.param("to", to);

        return statement.query(PostgresTransactionRepository::toListItem).list();
    }

    private static String listByUserSql(TransactionType type, LocalDateTime from, LocalDateTime to) {
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
        return sql.toString();
    }

    private static TransactionListItem toListItem(ResultSet rs, int rowNum) throws SQLException {
        return new TransactionListItem(
                rs.getString("id"),
                TransactionType.valueOf(rs.getString("type").toUpperCase(Locale.ROOT)),
                rs.getLong("amount"),
                rs.getString("sender_id"),
                rs.getString("sender_name"),
                rs.getString("recipient_id"),
                rs.getString("recipient_name"),
                rs.getObject("created_at", LocalDateTime.class));
    }
}
