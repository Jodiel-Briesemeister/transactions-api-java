package br.com.jodiel.transactionsapi.application.dtos.transaction;

import br.com.jodiel.transactionsapi.domain.enums.TransactionType;

import java.time.LocalDateTime;

public record TransactionResponse(
        String id,
        TransactionType type,
        long amount,
        String senderId,
        String senderName,
        String recipientId,
        String recipientName,
        LocalDateTime createdAt
) {}
