package br.com.jodiel.transactionsapi.presentation.controllers;

import br.com.jodiel.transactionsapi.application.dtos.user.UpdateProfileRequest;
import br.com.jodiel.transactionsapi.application.dtos.user.UserProfileResponse;
import br.com.jodiel.transactionsapi.application.usecases.user.DeactivateAccountUseCase;
import br.com.jodiel.transactionsapi.application.usecases.user.GetProfileUseCase;
import br.com.jodiel.transactionsapi.application.usecases.user.UpdateProfileUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
public class UserController {

    private final GetProfileUseCase getProfileUseCase;
    private final UpdateProfileUseCase updateProfileUseCase;
    private final DeactivateAccountUseCase deactivateAccountUseCase;

    public UserController(GetProfileUseCase getProfileUseCase, UpdateProfileUseCase updateProfileUseCase,
                          DeactivateAccountUseCase deactivateAccountUseCase) {
        this.getProfileUseCase = getProfileUseCase;
        this.updateProfileUseCase = updateProfileUseCase;
        this.deactivateAccountUseCase = deactivateAccountUseCase;
    }

    @GetMapping("/profile")
    public UserProfileResponse getProfile(@AuthenticationPrincipal String userId) {
        return getProfileUseCase.execute(userId);
    }

    @PatchMapping("/profile")
    public UserProfileResponse updateProfile(@AuthenticationPrincipal String userId,
                                              @Valid @RequestBody UpdateProfileRequest request) {
        return updateProfileUseCase.execute(userId, request.name(), request.email(), request.phone());
    }

    @DeleteMapping("/account")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@AuthenticationPrincipal String userId) {
        deactivateAccountUseCase.execute(userId);
    }
}
