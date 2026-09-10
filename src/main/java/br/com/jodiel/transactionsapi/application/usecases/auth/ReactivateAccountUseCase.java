package br.com.jodiel.transactionsapi.application.usecases.auth;

import br.com.jodiel.transactionsapi.application.dtos.auth.TokenResponse;
import br.com.jodiel.transactionsapi.domain.entities.User;
import br.com.jodiel.transactionsapi.domain.enums.NotificationTemplate;
import br.com.jodiel.transactionsapi.domain.enums.Queue;
import br.com.jodiel.transactionsapi.domain.errors.AppException;
import br.com.jodiel.transactionsapi.domain.interfaces.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ReactivateAccountUseCase {

    private static final Logger log = LoggerFactory.getLogger(ReactivateAccountUseCase.class);

    private final UserRepository userRepository;
    private final PasswordService passwordService;
    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;
    private final MessagePublisher messagePublisher;

    public ReactivateAccountUseCase(UserRepository userRepository, PasswordService passwordService,
                                     AuthService authService, RefreshTokenService refreshTokenService,
                                     MessagePublisher messagePublisher) {
        this.userRepository = userRepository;
        this.passwordService = passwordService;
        this.authService = authService;
        this.refreshTokenService = refreshTokenService;
        this.messagePublisher = messagePublisher;
    }

    @Transactional
    public TokenResponse execute(String email, String password) {
        User user = userRepository.findByEmail(email)
                .filter(u -> passwordService.verify(password, u.getPasswordHash()))
                .orElseThrow(() -> {
                    log.warn("Invalid credentials email={}", email);
                    return new AppException("Invalid credentials", 401);
                });

        if (user.isActive()) {
            throw new AppException("Account is already active", 409);
        }

        userRepository.reactivate(user.getId());
        log.info("User reactivated userId={}", user.getId());

        messagePublisher.publish(Queue.NOTIFICATIONS_EMAIL, Map.of(
                "templateId", NotificationTemplate.USER_REACTIVATED.getValue(),
                "userName", user.getName(),
                "userEmail", user.getEmail()
        ));

        String accessToken = authService.generateAccessToken(user.getId());
        String refreshToken = refreshTokenService.createForUser(user.getId());

        return new TokenResponse(accessToken, refreshToken);
    }
}
