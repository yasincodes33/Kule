package com.AgentSaasAplication.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank(message = "token boş olamaz") String token,
        @NotBlank(message = "Şifre boş olamaz") @Size(min = 8, message = "Şifre en az 8 karakter olmalı") String newPassword) {
}
