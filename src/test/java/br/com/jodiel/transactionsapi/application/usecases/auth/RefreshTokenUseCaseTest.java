package br.com.jodiel.transactionsapi.application.usecases.auth;

import br.com.jodiel.transactionsapi.application.dtos.auth.TokenResponse;
import br.com.jodiel.transactionsapi.domain.errors.AppException;
import br.com.jodiel.transactionsapi.domain.interfaces.AuthService;
import br.com.jodiel.transactionsapi.domain.interfaces.RefreshTokenRepository;
import br.com.jodiel.transactionsapi.domain.interfaces.RefreshTokenRepository.RefreshTokenData;
import br.com.jodiel.transactionsapi.domain.interfaces.RefreshTokenService;
import br.com.jodiel.transactionsapi.support.Fixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenUseCaseTest {

    @Mock private AuthService authService;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private RefreshTokenService refreshTokenService;

    @InjectMocks private RefreshTokenUseCase sut;

    @Test
    @DisplayName("rotates the token: the old one is deleted and a new pair is issued")
    void rotatesToken() {
        RefreshTokenData stored = new RefreshTokenData("token-id", Fixtures.USER_ID,
                LocalDateTime.now().plusDays(7));
        when(refreshTokenRepository.findByToken("old-refresh")).thenReturn(Optional.of(stored));
        when(authService.generateAccessToken(Fixtures.USER_ID)).thenReturn("new-access");
        when(refreshTokenService.createForUser(Fixtures.USER_ID)).thenReturn("new-refresh");

        TokenResponse result = sut.execute("old-refresh");

        assertThat(result.accessToken()).isEqualTo("new-access");
        assertThat(result.refreshToken()).isEqualTo("new-refresh");

        InOrder inOrder = inOrder(refreshTokenRepository, refreshTokenService);
        inOrder.verify(refreshTokenRepository).deleteByToken("old-refresh");
        inOrder.verify(refreshTokenService).createForUser(Fixtures.USER_ID);
    }

    @Test
    @DisplayName("rejects a token that is not stored")
    void rejectsUnknownToken() {
        when(refreshTokenRepository.findByToken("bogus")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut.execute("bogus"))
                .isInstanceOf(AppException.class)
                .hasMessage("Invalid refresh token")
                .extracting(e -> ((AppException) e).getStatusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("rejects an expired token and issues nothing")
    void rejectsExpiredToken() {
        RefreshTokenData expired = new RefreshTokenData("token-id", Fixtures.USER_ID,
                LocalDateTime.now().minusMinutes(1));
        when(refreshTokenRepository.findByToken("expired")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> sut.execute("expired"))
                .isInstanceOf(AppException.class)
                .hasMessage("Expired refresh token")
                .extracting(e -> ((AppException) e).getStatusCode()).isEqualTo(401);

        verify(authService, never()).generateAccessToken(any());
    }
}
