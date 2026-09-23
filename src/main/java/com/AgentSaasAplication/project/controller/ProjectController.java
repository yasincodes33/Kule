package com.AgentSaasAplication.project.controller;

import com.AgentSaasAplication.common.tenant.TenantContext;
import com.AgentSaasAplication.project.domain.Project;
import com.AgentSaasAplication.project.dto.CreateProjectRequest;
import com.AgentSaasAplication.project.dto.ProjectResponse;
import com.AgentSaasAplication.project.dto.UpdateProjectRequest;
import com.AgentSaasAplication.project.service.ProjectService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Koleksiyon uçları (oluşturma/listeleme) mimari plandaki gibi organizasyon altında,
 * tekil kayıt uçları (okuma/güncelleme) doğrudan /projects altında. Path'teki
 * organizationId, X-Organization-Id header'ıyla çapraz doğrulanıyor — identity modülündeki
 * MembershipController ile aynı desen.
 */
@RestController
@RequestMapping("/api/v1")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping("/organizations/{organizationId}/projects")
    public ResponseEntity<ProjectResponse> createProject(@PathVariable UUID organizationId,
                                                          @Valid @RequestBody CreateProjectRequest request) {
        assertPathMatchesTenant(organizationId);
        Project project = projectService.createProject(request.name(), request.repoUrl(), request.defaultBranch());
        return ResponseEntity.status(HttpStatus.CREATED).body(ProjectResponse.from(project));
    }

    @GetMapping("/organizations/{organizationId}/projects")
    public Page<ProjectResponse> listProjects(@PathVariable UUID organizationId, Pageable pageable) {
        assertPathMatchesTenant(organizationId);
        return projectService.listProjects(pageable).map(ProjectResponse::from);
    }

    @GetMapping("/projects/{projectId}")
    public ProjectResponse getProject(@PathVariable UUID projectId) {
        return ProjectResponse.from(projectService.getProject(projectId));
    }

    @PutMapping("/projects/{projectId}")
    public ProjectResponse updateProject(@PathVariable UUID projectId,
                                          @Valid @RequestBody UpdateProjectRequest request) {
        Project project = projectService.updateProject(projectId, request.name(), request.repoUrl(), request.defaultBranch());
        return ProjectResponse.from(project);
    }

    private void assertPathMatchesTenant(UUID pathOrganizationId) {
        if (!pathOrganizationId.equals(TenantContext.get())) {
            throw new AccessDeniedException("URL'deki organizationId, X-Organization-Id header'ıyla eşleşmiyor");
        }
    }
}
