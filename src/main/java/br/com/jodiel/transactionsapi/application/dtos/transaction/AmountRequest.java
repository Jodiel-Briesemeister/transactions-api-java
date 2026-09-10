package br.com.jodiel.transactionsapi.application.dtos.transaction;

import jakarta.validation.constraints.Positive;

public record AmountRequest(@Positive long amount) {}
