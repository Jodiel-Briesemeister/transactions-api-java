package br.com.jodiel.transactionsapi.presentation.controllers;

import br.com.jodiel.transactionsapi.application.dtos.auth.*;
import br.com.jodiel.transactionsapi.application.usecases.auth.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final RegisterUseCase registerUseCase;
    private final LoginUseCase loginUseCase;
    private final LogoutUseCase logoutUseCase;
    private final RefreshTokenUseCase refreshTokenUseCase;
    private final ReactivateAccountUseCase reactivateAccountUseCase;

    public AuthController(RegisterUseCase registerUseCase, LoginUseCase loginUseCase,
                          LogoutUseCase logoutUseCase, RefreshTokenUseCase refreshTokenUseCase,
                          ReactivateAccountUseCase reactivateAccountUseCase) {
        this.registerUseCase = registerUseCase;
        this.loginUseCase = loginUseCase;
        this.logoutUseCase = logoutUseCase;
        this.refreshTokenUseCase = refreshTokenUseCase;
        this.reactivateAccountUseCase = reactivateAccountUseCase;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public TokenResponse register(@Valid @RequestBody RegisterRequest request) {
        return registerUseCase.execute(request.name(), request.email(),
                request.password(), request.phone());
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return loginUseCase.execute(request.email(), request.password());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody LogoutRequest request, HttpServletRequest httpRequest) {
        String accessToken = extractToken(httpRequest);
        logoutUseCase.execute(request.refreshToken(), accessToken);
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return refreshTokenUseCase.execute(request.refreshToken());
    }

    @PostMapping("/reactivate")
    public TokenResponse reactivate(@Valid @RequestBody LoginRequest request) {
        return reactivateAccountUseCase.execute(request.email(), request.password());
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        return (header != null && header.startsWith("Bearer ")) ? header.substring(7) : "";
    }
}
