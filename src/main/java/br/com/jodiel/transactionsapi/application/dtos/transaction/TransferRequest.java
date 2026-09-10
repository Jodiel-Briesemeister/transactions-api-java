package br.com.jodiel.transactionsapi.application.dtos.transaction;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record TransferRequest(
        @NotBlank @Email String recipientEmail,
        @Positive long amount
) {}
