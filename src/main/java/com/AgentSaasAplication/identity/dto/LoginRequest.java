package com.AgentSaasAplication.identity.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "E-posta boş olamaz") String email,
        @NotBlank(message = "Şifre boş olamaz") String password) {
}
