package br.com.jodiel.transactionsapi.application.usecases.transaction;

import br.com.jodiel.transactionsapi.application.dtos.transaction.TransactionResponse;
import br.com.jodiel.transactionsapi.domain.enums.TransactionType;
import br.com.jodiel.transactionsapi.domain.interfaces.TransactionRepository;
import br.com.jodiel.transactionsapi.domain.interfaces.TransactionRepository.TransactionListItem;
import br.com.jodiel.transactionsapi.support.Fixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListTransactionsUseCaseTest {

    @Mock private TransactionRepository transactionRepository;

    @InjectMocks private ListTransactionsUseCase sut;

    @Test
    @DisplayName("maps repository rows to the response shape")
    void mapsRows() {
        LocalDateTime createdAt = LocalDateTime.now();
        TransactionListItem row = new TransactionListItem("tx-1", TransactionType.TRANSFER, 100L,
                Fixtures.USER_ID, "John Doe", Fixtures.RECIPIENT_ID, "Jane Doe", createdAt);
        when(transactionRepository.listByUser(Fixtures.USER_ID, null, null, null))
                .thenReturn(List.of(row));

        List<TransactionResponse> result = sut.execute(Fixtures.USER_ID, null, null, null);

        assertThat(result).singleElement().satisfies(tx -> {
            assertThat(tx.id()).isEqualTo("tx-1");
            assertThat(tx.type()).isEqualTo(TransactionType.TRANSFER);
            assertThat(tx.amount()).isEqualTo(100L);
            assertThat(tx.senderName()).isEqualTo("John Doe");
            assertThat(tx.recipientName()).isEqualTo("Jane Doe");
            assertThat(tx.createdAt()).isEqualTo(createdAt);
        });
    }

    @Test
    @DisplayName("passes the type and date filters straight through to the repository")
    void forwardsFilters() {
        LocalDateTime from = LocalDateTime.now().minusDays(7);
        LocalDateTime to = LocalDateTime.now();
        when(transactionRepository.listByUser(Fixtures.USER_ID, TransactionType.DEPOSIT, from, to))
                .thenReturn(List.of());

        List<TransactionResponse> result = sut.execute(Fixtures.USER_ID, TransactionType.DEPOSIT, from, to);

        assertThat(result).isEmpty();
        verify(transactionRepository).listByUser(Fixtures.USER_ID, TransactionType.DEPOSIT, from, to);
    }
}
