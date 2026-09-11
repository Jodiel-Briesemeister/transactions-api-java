package br.com.jodiel.transactionsapi.application.usecases.auth;

import br.com.jodiel.transactionsapi.application.dtos.auth.TokenResponse;
import br.com.jodiel.transactionsapi.domain.entities.User;
import br.com.jodiel.transactionsapi.domain.enums.Queue;
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
class ReactivateAccountUseCaseTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordService passwordService;
    @Mock private AuthService authService;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private MessagePublisher messagePublisher;
    @Captor private ArgumentCaptor<Map<String, Object>> messageCaptor;

    @InjectMocks private ReactivateAccountUseCase sut;

    @Test
    @DisplayName("reactivates a deactivated account and logs the user straight back in")
    void reactivatesAccount() {
        User inactive = Fixtures.user(Fixtures.USER_ID, "john@example.com", false);
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(inactive));
        when(passwordService.verify("secret123", "hashed-password")).thenReturn(true);
        when(authService.generateAccessToken(Fixtures.USER_ID)).thenReturn("access-token");
        when(refreshTokenService.createForUser(Fixtures.USER_ID)).thenReturn("refresh-token");

        TokenResponse result = sut.execute("john@example.com", "secret123");

        verify(userRepository).reactivate(Fixtures.USER_ID);
        assertThat(result.accessToken()).isEqualTo("access-token");

        verify(messagePublisher).publish(eq(Queue.NOTIFICATIONS_EMAIL), messageCaptor.capture());
        assertThat(messageCaptor.getValue()).containsEntry("templateId", "user_reactivated");
    }

    @Test
    @DisplayName("refuses to reactivate an account that is already active")
    void refusesActiveAccount() {
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(Fixtures.user()));
        when(passwordService.verify("secret123", "hashed-password")).thenReturn(true);

        assertThatThrownBy(() -> sut.execute("john@example.com", "secret123"))
                .isInstanceOf(AppException.class)
                .hasMessage("Account is already active")
                .extracting(e -> ((AppException) e).getStatusCode()).isEqualTo(409);

        verify(userRepository, never()).reactivate(any());
    }

    @Test
    @DisplayName("does not reveal whether the account exists when credentials are wrong")
    void rejectsBadCredentials() {
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(Fixtures.user()));
        when(passwordService.verify("wrong", "hashed-password")).thenReturn(false);

        assertThatThrownBy(() -> sut.execute("john@example.com", "wrong"))
                .isInstanceOf(AppException.class)
                .hasMessage("Invalid credentials")
                .extracting(e -> ((AppException) e).getStatusCode()).isEqualTo(401);

        verifyNoInteractions(messagePublisher);
    }
}
