package com.AgentSaasAplication.identity.controller;

import com.AgentSaasAplication.common.tenant.CurrentUserResolver;
import com.AgentSaasAplication.common.tenant.TenantContext;
import com.AgentSaasAplication.identity.domain.Membership;
import com.AgentSaasAplication.identity.dto.InviteMemberRequest;
import com.AgentSaasAplication.identity.dto.MembershipResponse;
import com.AgentSaasAplication.identity.dto.PendingInvitationResponse;
import com.AgentSaasAplication.identity.dto.TransferOwnershipRequest;
import com.AgentSaasAplication.identity.service.MembershipService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class MembershipController {

    private final MembershipService membershipService;
    private final CurrentUserResolver currentUserResolver;

    public MembershipController(MembershipService membershipService,
                                 CurrentUserResolver currentUserResolver) {
        this.membershipService = membershipService;
        this.currentUserResolver = currentUserResolver;
    }

    @PostMapping("/organizations/{organizationId}/memberships/invite")
    public ResponseEntity<MembershipResponse> inviteMember(@PathVariable UUID organizationId,
                                                             @Valid @RequestBody InviteMemberRequest request,
                                                             @AuthenticationPrincipal Jwt jwt) {
        assertPathMatchesTenant(organizationId);
        UUID inviterUserId = currentUserResolver.resolve(jwt);
        Membership membership = membershipService.inviteMember(organizationId, inviterUserId, request.email(), request.role());
        return ResponseEntity.status(HttpStatus.CREATED).body(MembershipResponse.from(membership));
    }

    /** Organizasyonun üyelerini listeler. PENDING davetleri de içerir; istemci status
     * alanına göre ayırır. */
    @GetMapping("/organizations/{organizationId}/memberships")
    public ResponseEntity<Page<MembershipResponse>> listMemberships(@PathVariable UUID organizationId, Pageable pageable) {
        assertPathMatchesTenant(organizationId);
        Page<MembershipResponse> page = membershipService.listOrganizationMembers(pageable).map(MembershipResponse::from);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/me/invitations")
    public List<PendingInvitationResponse> listMyPendingInvitations(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = currentUserResolver.resolve(jwt);
        return membershipService.listMyPendingInvitations(userId);
    }

    @PostMapping("/invitations/{membershipId}/accept")
    public MembershipResponse acceptInvitation(@PathVariable UUID membershipId,
                                                @AuthenticationPrincipal Jwt jwt) {
        UUID userId = currentUserResolver.resolve(jwt);
        Membership membership = membershipService.acceptInvitation(membershipId, userId);
        return MembershipResponse.from(membership);
    }

    @PostMapping("/invitations/{membershipId}/reject")
    public ResponseEntity<Void> rejectInvitation(@PathVariable UUID membershipId,
                                                  @AuthenticationPrincipal Jwt jwt) {
        UUID userId = currentUserResolver.resolve(jwt);
        membershipService.rejectInvitation(membershipId, userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/organizations/{organizationId}/memberships/{membershipId}")
    public ResponseEntity<Void> revokeMembership(@PathVariable UUID organizationId,
                                                  @PathVariable UUID membershipId,
                                                  @AuthenticationPrincipal Jwt jwt) {
        assertPathMatchesTenant(organizationId);
        UUID actingUserId = currentUserResolver.resolve(jwt);
        membershipService.revokeMembership(organizationId, actingUserId, membershipId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/organizations/{organizationId}/memberships/transfer-ownership")
    public ResponseEntity<Void> transferOwnership(@PathVariable UUID organizationId,
                                                   @Valid @RequestBody TransferOwnershipRequest request,
                                                   @AuthenticationPrincipal Jwt jwt) {
        assertPathMatchesTenant(organizationId);
        UUID currentOwnerUserId = currentUserResolver.resolve(jwt);
        membershipService.transferOwnership(organizationId, currentOwnerUserId, request.newOwnerUserId());
        return ResponseEntity.noContent().build();
    }

    private void assertPathMatchesTenant(UUID pathOrganizationId) {
        if (!pathOrganizationId.equals(TenantContext.get())) {
            throw new AccessDeniedException("URL'deki organizationId, X-Organization-Id header'ıyla eşleşmiyor");
        }
    }
}