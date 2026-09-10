package br.com.jodiel.transactionsapi.application.dtos.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(min = 2) String name,
        @Email String email,
        @Pattern(regexp = "^\\+?[1-9]\\d{7,14}$", message = "Invalid phone number") String phone
) {}
