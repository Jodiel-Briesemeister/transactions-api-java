package br.com.jodiel.transactionsapi.application.usecases.user;

import br.com.jodiel.transactionsapi.application.dtos.user.UserProfileResponse;
import br.com.jodiel.transactionsapi.domain.errors.AppException;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetProfileUseCaseTest {

    @Mock private UserRepository userRepository;

    @InjectMocks private GetProfileUseCase sut;

    @Test
    @DisplayName("returns the profile without ever exposing the password hash")
    void returnsProfile() {
        when(userRepository.findById(Fixtures.USER_ID)).thenReturn(Optional.of(Fixtures.user()));

        UserProfileResponse result = sut.execute(Fixtures.USER_ID);

        assertThat(result.id()).isEqualTo(Fixtures.USER_ID);
        assertThat(result.name()).isEqualTo("John Doe");
        assertThat(result.email()).isEqualTo("john@example.com");
        // UserProfileResponse has no password field at all; this documents that on purpose.
        assertThat(UserProfileResponse.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .doesNotContain("passwordHash", "password");
    }

    @Test
    @DisplayName("fails with 404 for an unknown user")
    void failsForUnknownUser() {
        when(userRepository.findById(Fixtures.USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut.execute(Fixtures.USER_ID))
                .isInstanceOf(AppException.class)
                .hasMessage("User not found")
                .extracting(e -> ((AppException) e).getStatusCode()).isEqualTo(404);
    }
}
