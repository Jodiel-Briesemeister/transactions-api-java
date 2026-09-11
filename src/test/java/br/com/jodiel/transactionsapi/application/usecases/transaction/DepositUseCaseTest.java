package br.com.jodiel.transactionsapi.application.usecases.transaction;

import br.com.jodiel.transactionsapi.domain.entities.Transaction;
import br.com.jodiel.transactionsapi.domain.enums.Queue;
import br.com.jodiel.transactionsapi.domain.enums.TransactionType;
import br.com.jodiel.transactionsapi.domain.errors.AppException;
import br.com.jodiel.transactionsapi.domain.interfaces.*;
import br.com.jodiel.transactionsapi.support.Fixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DepositUseCaseTest {

    @Mock private AccountRepository accountRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private UserRepository userRepository;
    @Mock private MessagePublisher messagePublisher;
    @Captor private ArgumentCaptor<Map<String, Object>> messageCaptor;

    @InjectMocks private DepositUseCase sut;

    @Test
    @DisplayName("credits the account and records a deposit")
    void deposits() {
        when(accountRepository.findByUserId(Fixtures.USER_ID))
                .thenReturn(Optional.of(Fixtures.account(Fixtures.USER_ID, 0L)));
        when(userRepository.findById(Fixtures.USER_ID)).thenReturn(Optional.of(Fixtures.user()));

        sut.execute(Fixtures.USER_ID, 250L);

        verify(accountRepository).updateBalance(Fixtures.USER_ID, 250L);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).create(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(captor.getValue().getAmount()).isEqualTo(250L);

        verify(messagePublisher).publish(eq(Queue.NOTIFICATIONS_EMAIL), messageCaptor.capture());
        assertThat(messageCaptor.getValue())
                .containsEntry("templateId", "transaction_deposit")
                .containsEntry("amount", 250L);
    }

    @Test
    @DisplayName("fails with 404 when the account is missing")
    void failsWithoutAccount() {
        when(accountRepository.findByUserId(Fixtures.USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut.execute(Fixtures.USER_ID, 100L))
                .isInstanceOf(AppException.class)
                .hasMessage("Account not found")
                .extracting(e -> ((AppException) e).getStatusCode()).isEqualTo(404);

        verifyNoInteractions(transactionRepository, messagePublisher);
    }

    @Test
    @DisplayName("refuses a non-positive amount")
    void refusesNonPositiveAmount() {
        assertThatThrownBy(() -> sut.execute(Fixtures.USER_ID, 0L))
                .isInstanceOf(AppException.class)
                .hasMessage("Amount must be greater than zero")
                .extracting(e -> ((AppException) e).getStatusCode()).isEqualTo(422);

        verifyNoInteractions(accountRepository, transactionRepository, messagePublisher);
    }
}
