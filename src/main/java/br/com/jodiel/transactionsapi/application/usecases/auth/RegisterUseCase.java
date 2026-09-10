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
public class RegisterUseCase {

    private static final Logger log = LoggerFactory.getLogger(RegisterUseCase.class);

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final PasswordService passwordService;
    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;
    private final MessagePublisher messagePublisher;

    public RegisterUseCase(UserRepository userRepository, AccountRepository accountRepository,
                           PasswordService passwordService, AuthService authService,
                           RefreshTokenService refreshTokenService, MessagePublisher messagePublisher) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.passwordService = passwordService;
        this.authService = authService;
        this.refreshTokenService = refreshTokenService;
        this.messagePublisher = messagePublisher;
    }

    @Transactional
    public TokenResponse execute(String name, String email, String password, String phone) {
        if (userRepository.findByEmail(email).isPresent()) {
            log.warn("User already exists email={}", email);
            throw new AppException("User already exists", 409);
        }

        String passwordHash = passwordService.hash(password);
        User user = User.create(name, email, passwordHash, phone);

        String userId = userRepository.create(user);
        accountRepository.create(userId);

        log.info("User created userId={}", userId);

        messagePublisher.publish(Queue.NOTIFICATIONS_EMAIL, Map.of(
                "templateId", NotificationTemplate.USER_REGISTERED.getValue(),
                "userName", name,
                "userEmail", email
        ));

        String accessToken = authService.generateAccessToken(userId);
        String refreshToken = refreshTokenService.createForUser(userId);

        return new TokenResponse(accessToken, refreshToken);
    }
}
