package com.AgentSaasAplication.identity.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateOrganizationRequest(@NotBlank(message = "İsim boş olamaz") String name) {
}
