package com.AgentSaasAplication.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "E-posta boş olamaz") @Email(message = "Geçersiz e-posta") String email,
        @NotBlank(message = "Şifre boş olamaz") @Size(min = 8, message = "Şifre en az 8 karakter olmalı") String password,
        String displayName) {
}
