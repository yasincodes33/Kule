package com.AgentSaasAplication.identity.controller;

import com.AgentSaasAplication.common.tenant.CurrentUserResolver;
import com.AgentSaasAplication.identity.domain.Organization;
import com.AgentSaasAplication.identity.service.OrganizationService;
import com.AgentSaasAplication.identity.dto.CreateOrganizationRequest;
import com.AgentSaasAplication.identity.dto.OrganizationResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class OrganizationController {

    private final OrganizationService organizationService;
    private final CurrentUserResolver currentUserResolver;

    public OrganizationController(OrganizationService organizationService,
                                   CurrentUserResolver currentUserResolver) {
        this.organizationService = organizationService;
        this.currentUserResolver = currentUserResolver;
    }

    @PostMapping("/organizations")
    public ResponseEntity<OrganizationResponse> createOrganization(@Valid @RequestBody CreateOrganizationRequest request,
                                                                     @AuthenticationPrincipal Jwt jwt) {
        UUID userId = currentUserResolver.resolve(jwt);
        Organization organization = organizationService.createOrganization(request.name(), userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(OrganizationResponse.from(organization));
    }

    @GetMapping("/me/organizations")
    public List<OrganizationResponse> listMyOrganizations(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = currentUserResolver.resolve(jwt);
        return organizationService.listMyOrganizations(userId).stream()
                .map(OrganizationResponse::from)
                .toList();
    }
}