package com.AgentSaasAplication.identity.dto;

public record AuthResponse(String accessToken, String refreshToken, long expiresIn) {
}
