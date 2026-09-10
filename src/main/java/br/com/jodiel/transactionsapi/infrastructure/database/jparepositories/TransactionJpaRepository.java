package br.com.jodiel.transactionsapi.infrastructure.database.jparepositories;

import br.com.jodiel.transactionsapi.infrastructure.database.jpa.TransactionJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TransactionJpaRepository extends JpaRepository<TransactionJpaEntity, UUID> {}
