package br.com.jodiel.transactionsapi.application.usecases.auth;

import br.com.jodiel.transactionsapi.domain.interfaces.AuthService;
import br.com.jodiel.transactionsapi.domain.interfaces.RefreshTokenRepository;
import br.com.jodiel.transactionsapi.domain.interfaces.TokenBlacklistService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LogoutUseCaseTest {

    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private TokenBlacklistService tokenBlacklistService;
    @Mock private AuthService authService;

    @InjectMocks private LogoutUseCase sut;

    @Test
    @DisplayName("revokes the refresh token and blacklists the access token for its remaining life")
    void revokesBothTokens() {
        when(authService.getTokenRemainingSeconds("access-token")).thenReturn(300L);

        sut.execute("refresh-token", "access-token");

        verify(refreshTokenRepository).deleteByToken("refresh-token");
        verify(tokenBlacklistService).add("access-token", 300L);
    }

    @Test
    @DisplayName("skips the blacklist when the access token has already expired")
    void skipsBlacklistForExpiredToken() {
        when(authService.getTokenRemainingSeconds("expired-token")).thenReturn(0L);

        sut.execute("refresh-token", "expired-token");

        verify(refreshTokenRepository).deleteByToken("refresh-token");
        verify(tokenBlacklistService, never()).add(any(), anyLong());
    }
}
