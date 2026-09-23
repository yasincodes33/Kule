package com.AgentSaasAplication.identity.repository;

import com.AgentSaasAplication.identity.domain.Membership;
import com.AgentSaasAplication.identity.domain.MembershipStatus;
import com.AgentSaasAplication.identity.domain.Role;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MembershipRepository extends JpaRepository<Membership, UUID> {
    Optional<Membership> findByTenantIdAndUserIdAndStatus(UUID tenantId, UUID userId, MembershipStatus status);
    /** Org'un tüm üyelerini (PENDING davetler dahil) listelemek için — bkz. MembershipService#listOrganizationMembers. */
    Page<Membership> findByTenantId(UUID tenantId, Pageable pageable);
    List<Membership> findByUserIdAndStatus(UUID userId, MembershipStatus status);
    List<Membership> findByInvitedEmailAndStatus(String invitedEmail, MembershipStatus status);
    boolean existsByTenantIdAndInvitedEmailAndStatus(UUID tenantId, String invitedEmail, MembershipStatus status);
    long countByTenantIdAndRoleAndStatus(UUID tenantId, Role role, MembershipStatus status);

    List<Membership> findByTenantIdAndRoleInAndStatus(UUID tenantId, List<Role> roles, MembershipStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Membership m WHERE m.tenantId = :tenantId AND m.userId = :userId AND m.status = :status")
    Optional<Membership> lockByTenantIdAndUserIdAndStatus(@Param("tenantId") UUID tenantId,
                                                           @Param("userId") UUID userId,
                                                           @Param("status") MembershipStatus status);
}