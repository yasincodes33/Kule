package com.AgentSaasAplication.project.repository;

import com.AgentSaasAplication.project.domain.Project;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    Page<Project> findByTenantId(UUID tenantId, Pageable pageable);

    List<Project> findByTenantId(UUID tenantId);

    boolean existsByTenantIdAndRepoUrl(UUID tenantId, String repoUrl);
}