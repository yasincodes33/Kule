package com.AgentSaasAplication.project.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateProjectRequest(
        @NotBlank(message = "Proje adı boş olamaz") String name,
        @NotBlank(message = "Repo URL boş olamaz") String repoUrl,
        String defaultBranch) {
}