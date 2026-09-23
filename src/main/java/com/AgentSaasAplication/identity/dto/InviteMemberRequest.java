package com.AgentSaasAplication.identity.dto;

import com.AgentSaasAplication.identity.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record InviteMemberRequest(
        @NotBlank(message = "E-posta boş olamaz") @Email(message = "Geçersiz e-posta") String email,
        @NotNull(message = "Rol boş olamaz") Role role) {
}
