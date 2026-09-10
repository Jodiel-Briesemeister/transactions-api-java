package br.com.jodiel.transactionsapi.application.usecases.auth;

import br.com.jodiel.transactionsapi.application.dtos.auth.TokenResponse;
import br.com.jodiel.transactionsapi.domain.entities.User;
import br.com.jodiel.transactionsapi.domain.errors.AppException;
import br.com.jodiel.transactionsapi.domain.interfaces.AuthService;
import br.com.jodiel.transactionsapi.domain.interfaces.PasswordService;
import br.com.jodiel.transactionsapi.domain.interfaces.RefreshTokenService;
import br.com.jodiel.transactionsapi.domain.interfaces.UserRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginUseCaseTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordService passwordService;
    @Mock private AuthService authService;
    @Mock private RefreshTokenService refreshTokenService;

    @InjectMocks private LoginUseCase sut;

    @Test
    @DisplayName("issues both tokens for valid credentials")
    void issuesTokens() {
        User user = Fixtures.user();
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(user));
        when(passwordService.verify("secret123", "hashed-password")).thenReturn(true);
        when(authService.generateAccessToken(Fixtures.USER_ID)).thenReturn("access-token");
        when(refreshTokenService.createForUser(Fixtures.USER_ID)).thenReturn("refresh-token");

        TokenResponse result = sut.execute("john@example.com", "secret123");

        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
    }

    @Test
    @DisplayName("rejects an unknown email with 401 and issues no token")
    void rejectsUnknownEmail() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut.execute("nobody@example.com", "secret123"))
                .isInstanceOf(AppException.class)
                .hasMessage("Invalid credentials")
                .extracting(e -> ((AppException) e).getStatusCode()).isEqualTo(401);

        verify(authService, never()).generateAccessToken(any());
    }

    @Test
    @DisplayName("rejects a wrong password with the same 401 as an unknown email")
    void rejectsWrongPassword() {
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(Fixtures.user()));
        when(passwordService.verify("wrong", "hashed-password")).thenReturn(false);

        assertThatThrownBy(() -> sut.execute("john@example.com", "wrong"))
                .isInstanceOf(AppException.class)
                .hasMessage("Invalid credentials")
                .extracting(e -> ((AppException) e).getStatusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("blocks a deactivated account with 403 ACCOUNT_INACTIVE")
    void blocksInactiveAccount() {
        User inactive = Fixtures.user(Fixtures.USER_ID, "john@example.com", false);
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(inactive));
        when(passwordService.verify("secret123", "hashed-password")).thenReturn(true);

        assertThatThrownBy(() -> sut.execute("john@example.com", "secret123"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> {
                    assertThat(((AppException) e).getStatusCode()).isEqualTo(403);
                    assertThat(((AppException) e).getCode()).isEqualTo("ACCOUNT_INACTIVE");
                });
    }
}
