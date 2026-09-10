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
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegisterUseCaseTest {

    @Mock private UserRepository userRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private PasswordService passwordService;
    @Mock private AuthService authService;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private MessagePublisher messagePublisher;

    @InjectMocks private RegisterUseCase sut;

    @Test
    @DisplayName("creates the user, opens an account for them and returns tokens")
    void registersUser() {
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.empty());
        when(passwordService.hash("secret123")).thenReturn("hashed-password");
        when(userRepository.create(any(User.class))).thenReturn(Fixtures.USER_ID);
        when(authService.generateAccessToken(Fixtures.USER_ID)).thenReturn("access-token");
        when(refreshTokenService.createForUser(Fixtures.USER_ID)).thenReturn("refresh-token");

        TokenResponse result = sut.execute("John Doe", "john@example.com", "secret123", "+5511999999999");

        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");

        // The account must exist before any balance operation can be attempted.
        InOrder inOrder = inOrder(userRepository, accountRepository);
        inOrder.verify(userRepository).create(any(User.class));
        inOrder.verify(accountRepository).create(Fixtures.USER_ID);
    }

    @Test
    @DisplayName("stores the password hash, never the raw password")
    void storesOnlyTheHash() {
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.empty());
        when(passwordService.hash("secret123")).thenReturn("hashed-password");
        when(userRepository.create(any(User.class))).thenReturn(Fixtures.USER_ID);
        when(authService.generateAccessToken(Fixtures.USER_ID)).thenReturn("access-token");
        when(refreshTokenService.createForUser(Fixtures.USER_ID)).thenReturn("refresh-token");

        sut.execute("John Doe", "john@example.com", "secret123", "+5511999999999");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).create(captor.capture());
        assertThat(captor.getValue().getPasswordHash())
                .isEqualTo("hashed-password")
                .isNotEqualTo("secret123");
    }

    @Test
    @DisplayName("queues a welcome notification")
    void publishesNotification() {
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.empty());
        when(passwordService.hash("secret123")).thenReturn("hashed-password");
        when(userRepository.create(any(User.class))).thenReturn(Fixtures.USER_ID);
        when(authService.generateAccessToken(Fixtures.USER_ID)).thenReturn("access-token");
        when(refreshTokenService.createForUser(Fixtures.USER_ID)).thenReturn("refresh-token");

        sut.execute("John Doe", "john@example.com", "secret123", "+5511999999999");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(messagePublisher).publish(eq(Queue.NOTIFICATIONS_EMAIL), captor.capture());
        assertThat(captor.getValue())
                .containsEntry("templateId", "user_registered")
                .containsEntry("userEmail", "john@example.com");
    }

    @Test
    @DisplayName("refuses a duplicate email with 409 and creates nothing")
    void refusesDuplicateEmail() {
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(Fixtures.user()));

        assertThatThrownBy(() -> sut.execute("John Doe", "john@example.com", "secret123", null))
                .isInstanceOf(AppException.class)
                .hasMessage("User already exists")
                .extracting(e -> ((AppException) e).getStatusCode()).isEqualTo(409);

        verify(userRepository, never()).create(any());
        verify(accountRepository, never()).create(any());
        verifyNoInteractions(messagePublisher);
    }
}
