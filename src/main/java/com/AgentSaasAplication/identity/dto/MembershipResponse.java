package com.AgentSaasAplication.identity.dto;

import com.AgentSaasAplication.identity.domain.Membership;
import com.AgentSaasAplication.identity.domain.MembershipStatus;
import com.AgentSaasAplication.identity.domain.Role;

import java.util.UUID;

public record MembershipResponse(UUID id, UUID organizationId, UUID userId, String invitedEmail, Role role,
		MembershipStatus status) {

	public static MembershipResponse from(Membership membership) {
		return new MembershipResponse(membership.getId(), membership.getTenantId(), membership.getUserId(),
				membership.getInvitedEmail(), membership.getRole(), membership.getStatus());
	}
}