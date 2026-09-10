package br.com.jodiel.transactionsapi.application.usecases.user;

import br.com.jodiel.transactionsapi.application.dtos.user.UserProfileResponse;
import br.com.jodiel.transactionsapi.domain.entities.User;
import br.com.jodiel.transactionsapi.domain.errors.AppException;
import br.com.jodiel.transactionsapi.domain.interfaces.UserRepository;
import br.com.jodiel.transactionsapi.support.Fixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UpdateProfileUseCaseTest {

    @Mock private UserRepository userRepository;

    @InjectMocks private UpdateProfileUseCase sut;

    @Test
    @DisplayName("updates the profile and returns the new values")
    void updatesProfile() {
        User updated = User.reconstitute(Fixtures.USER_ID, "New Name", "new@example.com",
                "hashed-password", "+5511888888888", true, LocalDateTime.now(), LocalDateTime.now());

        when(userRepository.findById(Fixtures.USER_ID)).thenReturn(Optional.of(Fixtures.user()));
        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(userRepository.update(Fixtures.USER_ID, "New Name", "new@example.com", "+5511888888888"))
                .thenReturn(updated);

        UserProfileResponse result = sut.execute(Fixtures.USER_ID, "New Name", "new@example.com",
                "+5511888888888");

        assertThat(result.name()).isEqualTo("New Name");
        assertThat(result.email()).isEqualTo("new@example.com");
    }

    @Test
    @DisplayName("does not check for a duplicate when the email is unchanged")
    void skipsDuplicateCheckWhenEmailUnchanged() {
        when(userRepository.findById(Fixtures.USER_ID)).thenReturn(Optional.of(Fixtures.user()));
        when(userRepository.update(Fixtures.USER_ID, "New Name", "john@example.com", null))
                .thenReturn(Fixtures.user());

        sut.execute(Fixtures.USER_ID, "New Name", "john@example.com", null);

        verify(userRepository, never()).findByEmail(any());
    }

    @Test
    @DisplayName("refuses an email that already belongs to someone else")
    void refusesTakenEmail() {
        User other = Fixtures.user(Fixtures.RECIPIENT_ID, "taken@example.com", true);
        when(userRepository.findById(Fixtures.USER_ID)).thenReturn(Optional.of(Fixtures.user()));
        when(userRepository.findByEmail("taken@example.com")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> sut.execute(Fixtures.USER_ID, null, "taken@example.com", null))
                .isInstanceOf(AppException.class)
                .hasMessage("Email already in use")
                .extracting(e -> ((AppException) e).getStatusCode()).isEqualTo(409);

        verify(userRepository, never()).update(any(), any(), any(), any());
    }

    @Test
    @DisplayName("fails with 404 for an unknown user")
    void failsForUnknownUser() {
        when(userRepository.findById(Fixtures.USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut.execute(Fixtures.USER_ID, "New Name", null, null))
                .isInstanceOf(AppException.class)
                .hasMessage("User not found")
                .extracting(e -> ((AppException) e).getStatusCode()).isEqualTo(404);
    }
}
