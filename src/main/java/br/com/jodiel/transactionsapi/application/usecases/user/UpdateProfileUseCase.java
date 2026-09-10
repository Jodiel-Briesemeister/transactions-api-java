package br.com.jodiel.transactionsapi.application.usecases.user;

import br.com.jodiel.transactionsapi.application.dtos.user.UserProfileResponse;
import br.com.jodiel.transactionsapi.domain.entities.User;
import br.com.jodiel.transactionsapi.domain.errors.AppException;
import br.com.jodiel.transactionsapi.domain.interfaces.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class UpdateProfileUseCase {

    private static final Logger log = LoggerFactory.getLogger(UpdateProfileUseCase.class);

    private final UserRepository userRepository;

    public UpdateProfileUseCase(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserProfileResponse execute(String userId, String name, String email, String phone) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.warn("User not found userId={}", userId);
                    return new AppException("User not found", 404);
                });

        if (email != null && !email.equals(user.getEmail())) {
            userRepository.findByEmail(email).ifPresent(existing -> {
                log.warn("Email already in use email={}", email);
                throw new AppException("Email already in use", 409);
            });
        }

        User updated = userRepository.update(userId, name, email, phone);
        log.info("User updated userId={}", userId);

        return new UserProfileResponse(updated.getId(), updated.getName(),
                updated.getEmail(), updated.getPhone(), updated.getCreatedAt());
    }
}
