package br.com.jodiel.transactionsapi.application.usecases.auth;

import br.com.jodiel.transactionsapi.application.dtos.auth.TokenResponse;
import br.com.jodiel.transactionsapi.domain.entities.User;
import br.com.jodiel.transactionsapi.domain.errors.AppException;
import br.com.jodiel.transactionsapi.domain.interfaces.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LoginUseCase {

    private static final Logger log = LoggerFactory.getLogger(LoginUseCase.class);

    private final UserRepository userRepository;
    private final PasswordService passwordService;
    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;

    public LoginUseCase(UserRepository userRepository, PasswordService passwordService,
                        AuthService authService, RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.passwordService = passwordService;
        this.authService = authService;
        this.refreshTokenService = refreshTokenService;
    }

    public TokenResponse execute(String email, String password) {
        User user = userRepository.findByEmail(email)
                .filter(u -> passwordService.verify(password, u.getPasswordHash()))
                .orElseThrow(() -> {
                    log.warn("Invalid credentials email={}", email);
                    return new AppException("Invalid credentials", 401);
                });

        if (!user.isActive()) {
            throw new AppException("Account is inactive", 403, "ACCOUNT_INACTIVE");
        }

        String accessToken = authService.generateAccessToken(user.getId());
        String refreshToken = refreshTokenService.createForUser(user.getId());

        log.info("User logged in userId={}", user.getId());
        return new TokenResponse(accessToken, refreshToken);
    }
}
