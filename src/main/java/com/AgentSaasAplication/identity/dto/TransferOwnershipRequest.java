package com.AgentSaasAplication.identity.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record TransferOwnershipRequest(@NotNull(message = "newOwnerUserId boş olamaz") UUID newOwnerUserId) {
}
