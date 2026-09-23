package com.AgentSaasAplication.project.service;

import com.AgentSaasAplication.common.exceptions.NotFoundException;
import com.AgentSaasAplication.common.tenant.TenantContext;
import com.AgentSaasAplication.project.domain.Project;
import com.AgentSaasAplication.project.repository.ProjectRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@Transactional(readOnly = true)
public class ProjectService {

    private final ProjectRepository projectRepository;

    public ProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @Transactional
    public Project createProject(String name, String repoUrl, String defaultBranch) {
        UUID organizationId = TenantContext.get();

        if (projectRepository.existsByTenantIdAndRepoUrl(organizationId, repoUrl)) {
            throw new IllegalStateException("Bu organizasyonda aynı repo URL'si zaten kayıtlı: " + repoUrl);
        }

        Project project = (defaultBranch != null && !defaultBranch.isBlank())
                ? Project.create(organizationId, name, repoUrl, defaultBranch)
                : Project.create(organizationId, name, repoUrl);

        project = projectRepository.save(project);

        log.info("Proje oluşturuldu: projectId={}, organizationId={}, repoUrl={}",
                project.getId(), organizationId, repoUrl);

        return project;
    }

    public Page<Project> listProjects(Pageable pageable) {
        UUID organizationId = TenantContext.get();
        return projectRepository.findByTenantId(organizationId, pageable);
    }

    public Project getProject(UUID projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Proje bulunamadı: " + projectId));
    }

    /**
     * task/runner modüllerinin projectId doğrulaması için kullandığı dar kapsamlı bir varlık
     * kontrolü: bu iki modül project.repository.ProjectRepository'ye doğrudan değil yalnızca
     * bu servis üzerinden erişir, böylece aggregate sınırı korunur.
     *
     * Bilinçli olarak {@link #getProject} değil: o, GET /projects/{id} uç noktasının
     * kullandığı ve 400 (IllegalArgumentException) döndüren ayrı bir yol. Bu metot ise
     * task/runner tarafının beklediği 404 (NotFoundException) semantiğini korur.
     */
    public void ensureProjectExists(UUID projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new NotFoundException("Proje bulunamadı veya yetkiniz yok: " + projectId);
        }
    }

    @Transactional
    public Project updateProject(UUID projectId, String name, String repoUrl, String defaultBranch) {
        Project project = getProject(projectId);
        project.updateRepoSettings(name, repoUrl, defaultBranch);

        log.info("Proje güncellendi: projectId={}, organizationId={}",
                project.getId(), project.getTenantId());

        return project;
    }
}