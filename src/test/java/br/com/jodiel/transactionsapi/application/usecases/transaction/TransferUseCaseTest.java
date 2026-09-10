package br.com.jodiel.transactionsapi.application.usecases.transaction;

import br.com.jodiel.transactionsapi.domain.entities.Account;
import br.com.jodiel.transactionsapi.domain.entities.Transaction;
import br.com.jodiel.transactionsapi.domain.entities.User;
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
class TransferUseCaseTest {

    @Mock private UserRepository userRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private MessagePublisher messagePublisher;

    @InjectMocks private TransferUseCase sut;

    private void givenBothAccountsExist(long senderBalance) {
        when(accountRepository.findByUserId(Fixtures.USER_ID))
                .thenReturn(Optional.of(Fixtures.account(Fixtures.USER_ID, senderBalance)));
        when(accountRepository.findByUserIdForUpdate(Fixtures.USER_ID))
                .thenReturn(Optional.of(Fixtures.account(Fixtures.USER_ID, senderBalance)));
        when(accountRepository.findByUserIdForUpdate(Fixtures.RECIPIENT_ID))
                .thenReturn(Optional.of(Fixtures.account(Fixtures.RECIPIENT_ID, 0L)));
    }

    @Test
    @DisplayName("debits the sender, credits the recipient and records one transfer row")
    void movesMoney() {
        givenBothAccountsExist(500L);
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(Fixtures.recipient()));
        when(userRepository.findById(Fixtures.USER_ID)).thenReturn(Optional.of(Fixtures.user()));

        sut.execute(Fixtures.USER_ID, "jane@example.com", 100L);

        verify(accountRepository).updateBalance(Fixtures.USER_ID, -100L);
        verify(accountRepository).updateBalance(Fixtures.RECIPIENT_ID, 100L);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).create(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(TransactionType.TRANSFER);
        assertThat(captor.getValue().getAmount()).isEqualTo(100L);
        assertThat(captor.getValue().getRecipientId()).isEqualTo(Fixtures.RECIPIENT_ID);
    }

    @Test
    @DisplayName("reads the balance under a write lock, not with an unlocked read")
    void readsBalanceUnderLock() {
        givenBothAccountsExist(500L);
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(Fixtures.recipient()));
        when(userRepository.findById(Fixtures.USER_ID)).thenReturn(Optional.of(Fixtures.user()));

        sut.execute(Fixtures.USER_ID, "jane@example.com", 100L);

        // Both rows must be locked before any money moves, otherwise two concurrent transfers
        // can each see the same balance and overdraw the account.
        verify(accountRepository).findByUserIdForUpdate(Fixtures.USER_ID);
        verify(accountRepository).findByUserIdForUpdate(Fixtures.RECIPIENT_ID);
    }

    @Test
    @DisplayName("notifies both sides of the transfer")
    void notifiesBothParties() {
        givenBothAccountsExist(500L);
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(Fixtures.recipient()));
        when(userRepository.findById(Fixtures.USER_ID)).thenReturn(Optional.of(Fixtures.user()));

        sut.execute(Fixtures.USER_ID, "jane@example.com", 100L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(messagePublisher, times(2)).publish(eq(Queue.NOTIFICATIONS_EMAIL), captor.capture());

        assertThat(captor.getAllValues()).extracting(m -> m.get("templateId"))
                .containsExactly("transaction_transfer_sent", "transaction_transfer_received");
        assertThat(captor.getAllValues()).extracting(m -> m.get("userEmail"))
                .containsExactly("john@example.com", "jane@example.com");
    }

    @Test
    @DisplayName("refuses a transfer larger than the balance")
    void refusesInsufficientBalance() {
        givenBothAccountsExist(50L);
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(Fixtures.recipient()));

        assertThatThrownBy(() -> sut.execute(Fixtures.USER_ID, "jane@example.com", 100L))
                .isInstanceOf(AppException.class)
                .hasMessage("Insufficient balance")
                .extracting(e -> ((AppException) e).getStatusCode()).isEqualTo(422);

        verify(accountRepository, never()).updateBalance(any(), anyLong());
        verifyNoInteractions(transactionRepository, messagePublisher);
    }

    @Test
    @DisplayName("refuses a transfer to yourself")
    void refusesSelfTransfer() {
        User self = Fixtures.user(Fixtures.USER_ID, "john@example.com", true);
        when(accountRepository.findByUserId(Fixtures.USER_ID))
                .thenReturn(Optional.of(Fixtures.account(Fixtures.USER_ID, 500L)));
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(self));

        assertThatThrownBy(() -> sut.execute(Fixtures.USER_ID, "john@example.com", 100L))
                .isInstanceOf(AppException.class)
                .hasMessage("Cannot transfer to yourself")
                .extracting(e -> ((AppException) e).getStatusCode()).isEqualTo(422);
    }

    @Test
    @DisplayName("refuses a transfer to a deactivated account")
    void refusesInactiveRecipient() {
        User inactive = Fixtures.user(Fixtures.RECIPIENT_ID, "jane@example.com", false);
        when(accountRepository.findByUserId(Fixtures.USER_ID))
                .thenReturn(Optional.of(Fixtures.account(Fixtures.USER_ID, 500L)));
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> sut.execute(Fixtures.USER_ID, "jane@example.com", 100L))
                .isInstanceOf(AppException.class)
                .hasMessage("Recipient account is inactive")
                .extracting(e -> ((AppException) e).getStatusCode()).isEqualTo(422);
    }

    @Test
    @DisplayName("refuses an unknown recipient")
    void refusesUnknownRecipient() {
        when(accountRepository.findByUserId(Fixtures.USER_ID))
                .thenReturn(Optional.of(Fixtures.account(Fixtures.USER_ID, 500L)));
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut.execute(Fixtures.USER_ID, "nobody@example.com", 100L))
                .isInstanceOf(AppException.class)
                .hasMessage("Recipient not found")
                .extracting(e -> ((AppException) e).getStatusCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("refuses a non-positive amount before touching any repository")
    void refusesNonPositiveAmount() {
        assertThatThrownBy(() -> sut.execute(Fixtures.USER_ID, "jane@example.com", 0L))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getStatusCode()).isEqualTo(422);

        verifyNoInteractions(accountRepository, userRepository, transactionRepository, messagePublisher);
    }

    @Test
    @DisplayName("locks the two accounts in a stable id order so opposite transfers cannot deadlock")
    void locksInStableOrder() {
        // Sender id sorts after the recipient id here, so the recipient must be locked first.
        String highSenderId = "99999999-9999-4999-8999-999999999999";
        when(accountRepository.findByUserId(highSenderId))
                .thenReturn(Optional.of(Fixtures.account(highSenderId, 500L)));
        when(accountRepository.findByUserIdForUpdate(highSenderId))
                .thenReturn(Optional.of(Fixtures.account(highSenderId, 500L)));
        when(accountRepository.findByUserIdForUpdate(Fixtures.RECIPIENT_ID))
                .thenReturn(Optional.of(Fixtures.account(Fixtures.RECIPIENT_ID, 0L)));
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(Fixtures.recipient()));
        when(userRepository.findById(highSenderId)).thenReturn(Optional.of(Fixtures.user()));

        sut.execute(highSenderId, "jane@example.com", 100L);

        var inOrder = inOrder(accountRepository);
        inOrder.verify(accountRepository).findByUserIdForUpdate(Fixtures.RECIPIENT_ID);
        inOrder.verify(accountRepository).findByUserIdForUpdate(highSenderId);
    }
}
