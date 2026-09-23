package com.AgentSaasAplication.project.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateProjectRequest(
        @NotBlank(message = "Proje adı boş olamaz") String name,
        @NotBlank(message = "Repo URL boş olamaz") String repoUrl,
        @NotBlank(message = "Branch adı boş olamaz") String defaultBranch) {
}