package br.com.jodiel.transactionsapi.application.usecases.user;

import br.com.jodiel.transactionsapi.domain.enums.NotificationTemplate;
import br.com.jodiel.transactionsapi.domain.enums.Queue;
import br.com.jodiel.transactionsapi.domain.errors.AppException;
import br.com.jodiel.transactionsapi.domain.interfaces.MessagePublisher;
import br.com.jodiel.transactionsapi.domain.interfaces.RefreshTokenRepository;
import br.com.jodiel.transactionsapi.domain.interfaces.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class DeactivateAccountUseCase {

    private static final Logger log = LoggerFactory.getLogger(DeactivateAccountUseCase.class);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final MessagePublisher messagePublisher;

    public DeactivateAccountUseCase(UserRepository userRepository,
                                     RefreshTokenRepository refreshTokenRepository,
                                     MessagePublisher messagePublisher) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.messagePublisher = messagePublisher;
    }

    @Transactional
    public void execute(String userId) {
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException("User not found", 404));

        refreshTokenRepository.deleteAllByUser(userId);
        userRepository.deactivate(userId);

        log.info("User deactivated userId={}", userId);

        messagePublisher.publish(Queue.NOTIFICATIONS_EMAIL, Map.of(
                "templateId", NotificationTemplate.USER_DEACTIVATED.getValue(),
                "userName", user.getName(),
                "userEmail", user.getEmail()
        ));
    }
}
