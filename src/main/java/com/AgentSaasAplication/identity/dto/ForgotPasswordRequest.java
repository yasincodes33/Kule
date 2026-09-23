package com.AgentSaasAplication.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequest(
        @NotBlank(message = "E-posta boş olamaz") @Email(message = "Geçersiz e-posta") String email) {
}
