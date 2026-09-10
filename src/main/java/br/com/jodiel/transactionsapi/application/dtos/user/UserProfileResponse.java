package br.com.jodiel.transactionsapi.application.dtos.user;

import java.time.LocalDateTime;

public record UserProfileResponse(
        String id,
        String name,
        String email,
        String phone,
        LocalDateTime createdAt
) {}
