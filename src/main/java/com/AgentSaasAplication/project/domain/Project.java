package com.AgentSaasAplication.project.domain;

import com.AgentSaasAplication.common.entity.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "projects")
public class Project extends TenantScopedEntity {

    @Column(nullable = false)
    private String name;

    @Column(name = "repo_url", nullable = false)
    private String repoUrl;

    @Column(name = "default_branch", nullable = false)
    private String defaultBranch;

    private Project(UUID organizationId, String name, String repoUrl, String defaultBranch) {
        super(organizationId);
        this.name = name;
        this.repoUrl = repoUrl;
        this.defaultBranch = defaultBranch;
    }

    public static Project create(UUID organizationId, String name, String repoUrl, String defaultBranch) {
        return new Project(organizationId, name, repoUrl, defaultBranch);
    }

    public static Project create(UUID organizationId, String name, String repoUrl) {
        return new Project(organizationId, name, repoUrl, "main");
    }

    public void updateRepoSettings(String name, String repoUrl, String defaultBranch) {
        this.name = name;
        this.repoUrl = repoUrl;
        this.defaultBranch = defaultBranch;
    }
}