package br.com.jodiel.transactionsapi.application.dtos.transaction;

import java.time.LocalDateTime;

public record TransactionResponse(
        String id,
        String type,
        long amount,
        String senderId,
        String senderName,
        String recipientId,
        String recipientName,
        LocalDateTime createdAt
) {}
