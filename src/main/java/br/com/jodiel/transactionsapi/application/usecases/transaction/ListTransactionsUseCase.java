package br.com.jodiel.transactionsapi.application.usecases.transaction;

import br.com.jodiel.transactionsapi.application.dtos.transaction.TransactionResponse;
import br.com.jodiel.transactionsapi.domain.enums.TransactionType;
import br.com.jodiel.transactionsapi.domain.interfaces.TransactionRepository;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ListTransactionsUseCase {

    private final TransactionRepository transactionRepository;

    public ListTransactionsUseCase(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public List<TransactionResponse> execute(String userId, TransactionType type,
                                              LocalDateTime from, LocalDateTime to) {
        return transactionRepository.listByUser(userId, type, from, to).stream()
                .map(item -> new TransactionResponse(
                        item.id(), item.type().getValue(), item.amount(),
                        item.senderId(), item.senderName(),
                        item.recipientId(), item.recipientName(),
                        item.createdAt()
                ))
                .toList();
    }
}
