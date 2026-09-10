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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WithdrawUseCaseTest {

    @Mock private AccountRepository accountRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private UserRepository userRepository;
    @Mock private MessagePublisher messagePublisher;

    @InjectMocks private WithdrawUseCase sut;

    @Test
    @DisplayName("debits the account and records a withdraw")
    void withdraws() {
        when(accountRepository.findByUserIdForUpdate(Fixtures.USER_ID))
                .thenReturn(Optional.of(Fixtures.account(Fixtures.USER_ID, 500L)));
        when(userRepository.findById(Fixtures.USER_ID)).thenReturn(Optional.of(Fixtures.user()));

        sut.execute(Fixtures.USER_ID, 200L);

        verify(accountRepository).updateBalance(Fixtures.USER_ID, -200L);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).create(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(TransactionType.WITHDRAW);
        assertThat(captor.getValue().getAmount()).isEqualTo(200L);
        assertThat(captor.getValue().getRecipientId()).isNull();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> message = ArgumentCaptor.forClass(Map.class);
        verify(messagePublisher).publish(eq(Queue.NOTIFICATIONS_EMAIL), message.capture());
        assertThat(message.getValue()).containsEntry("templateId", "transaction_withdraw");
    }

    @Test
    @DisplayName("reads the balance under a write lock")
    void readsBalanceUnderLock() {
        when(accountRepository.findByUserIdForUpdate(Fixtures.USER_ID))
                .thenReturn(Optional.of(Fixtures.account(Fixtures.USER_ID, 500L)));
        when(userRepository.findById(Fixtures.USER_ID)).thenReturn(Optional.of(Fixtures.user()));

        sut.execute(Fixtures.USER_ID, 200L);

        verify(accountRepository).findByUserIdForUpdate(Fixtures.USER_ID);
        verify(accountRepository, never()).findByUserId(any());
    }

    @Test
    @DisplayName("refuses to overdraw the account")
    void refusesOverdraft() {
        when(accountRepository.findByUserIdForUpdate(Fixtures.USER_ID))
                .thenReturn(Optional.of(Fixtures.account(Fixtures.USER_ID, 50L)));

        assertThatThrownBy(() -> sut.execute(Fixtures.USER_ID, 100L))
                .isInstanceOf(AppException.class)
                .hasMessage("Insufficient balance")
                .extracting(e -> ((AppException) e).getStatusCode()).isEqualTo(422);

        verify(accountRepository, never()).updateBalance(any(), anyLong());
        verifyNoInteractions(transactionRepository, messagePublisher);
    }

    @Test
    @DisplayName("fails with 404 when the account is missing")
    void failsWithoutAccount() {
        when(accountRepository.findByUserIdForUpdate(Fixtures.USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut.execute(Fixtures.USER_ID, 100L))
                .isInstanceOf(AppException.class)
                .hasMessage("Account not found")
                .extracting(e -> ((AppException) e).getStatusCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("refuses a non-positive amount")
    void refusesNonPositiveAmount() {
        assertThatThrownBy(() -> sut.execute(Fixtures.USER_ID, -5L))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getStatusCode()).isEqualTo(422);

        verifyNoInteractions(accountRepository, transactionRepository, messagePublisher);
    }
}
