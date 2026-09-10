package br.com.jodiel.transactionsapi.application.dtos.auth;

public record TokenResponse(String accessToken, String refreshToken) {}
