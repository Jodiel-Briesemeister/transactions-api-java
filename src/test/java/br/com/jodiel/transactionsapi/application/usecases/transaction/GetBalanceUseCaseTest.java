package br.com.jodiel.transactionsapi.application.usecases.transaction;

import br.com.jodiel.transactionsapi.application.dtos.transaction.BalanceResponse;
import br.com.jodiel.transactionsapi.domain.errors.AppException;
import br.com.jodiel.transactionsapi.domain.interfaces.AccountRepository;
import br.com.jodiel.transactionsapi.support.Fixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetBalanceUseCaseTest {

    @Mock private AccountRepository accountRepository;

    @InjectMocks private GetBalanceUseCase sut;

    @Test
    @DisplayName("returns the current balance")
    void returnsBalance() {
        when(accountRepository.findByUserId(Fixtures.USER_ID))
                .thenReturn(Optional.of(Fixtures.account(Fixtures.USER_ID, 1_250L)));

        BalanceResponse result = sut.execute(Fixtures.USER_ID);

        assertThat(result.balance()).isEqualTo(1_250L);
    }

    @Test
    @DisplayName("fails with 404 when the account is missing")
    void failsWithoutAccount() {
        when(accountRepository.findByUserId(Fixtures.USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut.execute(Fixtures.USER_ID))
                .isInstanceOf(AppException.class)
                .hasMessage("Account not found")
                .extracting(e -> ((AppException) e).getStatusCode()).isEqualTo(404);
    }
}
