package com.AgentSaasAplication.project.dto;

import com.AgentSaasAplication.project.domain.Project;

import java.util.UUID;

public record ProjectResponse(UUID id, String name, String repoUrl, String defaultBranch) {

    public static ProjectResponse from(Project project) {
        return new ProjectResponse(project.getId(), project.getName(), project.getRepoUrl(), project.getDefaultBranch());
    }
}