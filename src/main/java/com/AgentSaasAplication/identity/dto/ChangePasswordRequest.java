package com.AgentSaasAplication.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "Mevcut şifre boş olamaz") String currentPassword,
        @NotBlank(message = "Şifre boş olamaz") @Size(min = 8, message = "Şifre en az 8 karakter olmalı") String newPassword) {
}
