package br.com.jodiel.transactionsapi.application.usecases.user;

import br.com.jodiel.transactionsapi.domain.enums.Queue;
import br.com.jodiel.transactionsapi.domain.errors.AppException;
import br.com.jodiel.transactionsapi.domain.interfaces.MessagePublisher;
import br.com.jodiel.transactionsapi.domain.interfaces.RefreshTokenRepository;
import br.com.jodiel.transactionsapi.domain.interfaces.UserRepository;
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
class DeactivateAccountUseCaseTest {

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private MessagePublisher messagePublisher;
    @Captor private ArgumentCaptor<Map<String, Object>> messageCaptor;

    @InjectMocks private DeactivateAccountUseCase sut;

    @Test
    @DisplayName("deactivates the user and revokes every outstanding refresh token")
    void deactivatesAndRevokesSessions() {
        when(userRepository.findById(Fixtures.USER_ID)).thenReturn(Optional.of(Fixtures.user()));

        sut.execute(Fixtures.USER_ID);

        // Leaving refresh tokens alive would let a deactivated user mint new access tokens.
        verify(refreshTokenRepository).deleteAllByUser(Fixtures.USER_ID);
        verify(userRepository).deactivate(Fixtures.USER_ID);

        verify(messagePublisher).publish(eq(Queue.NOTIFICATIONS_EMAIL), messageCaptor.capture());
        assertThat(messageCaptor.getValue()).containsEntry("templateId", "user_deactivated");
    }

    @Test
    @DisplayName("fails with 404 for an unknown user and changes nothing")
    void failsForUnknownUser() {
        when(userRepository.findById(Fixtures.USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut.execute(Fixtures.USER_ID))
                .isInstanceOf(AppException.class)
                .hasMessage("User not found")
                .extracting(e -> ((AppException) e).getStatusCode()).isEqualTo(404);

        verify(userRepository, never()).deactivate(any());
        verifyNoInteractions(refreshTokenRepository, messagePublisher);
    }
}
