package br.com.jodiel.transactionsapi.application.dtos.auth;

import br.com.jodiel.transactionsapi.application.validation.StrongPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(min = 2) String name,
        @NotBlank @Email String email,
        @NotBlank @StrongPassword String password,
        @Pattern(regexp = "^\\+?[1-9]\\d{7,14}$", message = "Invalid phone number") String phone
) {}
