package com.AgentSaasAplication.identity.dto;

import com.AgentSaasAplication.identity.domain.Role;

import java.util.UUID;

/**
 * `/me/invitations`'a özel — `MembershipResponse`'tan farklı olarak
 * `organizationName` taşır: davet edilen kullanıcı henüz o organizasyonun üyesi olmadığı için
 * (PENDING), `organizations` listesinde ismini göremez — daveti kabul/red edebilmesi için hangi
 * organizasyon olduğunu bilmesi gerekir.
 */
public record PendingInvitationResponse(UUID membershipId, UUID organizationId, String organizationName, Role role) {
}
