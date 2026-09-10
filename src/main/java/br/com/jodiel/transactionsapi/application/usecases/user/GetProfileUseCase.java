package br.com.jodiel.transactionsapi.application.usecases.user;

import br.com.jodiel.transactionsapi.application.dtos.user.UserProfileResponse;
import br.com.jodiel.transactionsapi.domain.errors.AppException;
import br.com.jodiel.transactionsapi.domain.interfaces.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class GetProfileUseCase {

    private static final Logger log = LoggerFactory.getLogger(GetProfileUseCase.class);

    private final UserRepository userRepository;

    public GetProfileUseCase(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserProfileResponse execute(String userId) {
        return userRepository.findById(userId)
                .map(u -> new UserProfileResponse(u.getId(), u.getName(), u.getEmail(), u.getPhone(), u.getCreatedAt()))
                .orElseThrow(() -> {
                    log.warn("User not found userId={}", userId);
                    return new AppException("User not found", 404);
                });
    }
}
