package com.AgentSaasAplication.identity.service;

import com.AgentSaasAplication.common.audit.AuditEvent;
import com.AgentSaasAplication.common.email.EmailService;
import com.AgentSaasAplication.common.exceptions.NotFoundException;
import com.AgentSaasAplication.common.tenant.RlsSessionVariables;
import com.AgentSaasAplication.common.tenant.TenantContext;
import com.AgentSaasAplication.identity.domain.Membership;
import com.AgentSaasAplication.identity.domain.MembershipStatus;
import com.AgentSaasAplication.identity.domain.Organization;
import com.AgentSaasAplication.identity.domain.Role;
import com.AgentSaasAplication.identity.domain.User;
import com.AgentSaasAplication.identity.dto.PendingInvitationResponse;
import com.AgentSaasAplication.identity.repository.MembershipRepository;
import com.AgentSaasAplication.identity.repository.OrganizationRepository;
import com.AgentSaasAplication.identity.repository.UserRepository;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional(readOnly = true)
public class MembershipService {

	private final MembershipRepository membershipRepository;
	private final UserRepository userRepository;
	private final OrganizationRepository organizationRepository;
	private final EntityManager entityManager;
	private final MembershipAuthorizationService membershipAuthorizationService;
	private final ApplicationEventPublisher eventPublisher;
	private final EmailService emailService;

	public MembershipService(MembershipRepository membershipRepository, UserRepository userRepository,
			OrganizationRepository organizationRepository, EntityManager entityManager,
			MembershipAuthorizationService membershipAuthorizationService,
			ApplicationEventPublisher eventPublisher, EmailService emailService) {
		this.membershipRepository = membershipRepository;
		this.userRepository = userRepository;
		this.organizationRepository = organizationRepository;
		this.entityManager = entityManager;
		this.membershipAuthorizationService = membershipAuthorizationService;
		this.eventPublisher = eventPublisher;
		this.emailService = emailService;
	}

	@Transactional
	public Membership inviteMember(UUID organizationId, UUID inviterUserId, String inviteeEmail, Role role) {
		requireAdminOrOwner(organizationId, inviterUserId);

		if (membershipRepository.existsByTenantIdAndInvitedEmailAndStatus(organizationId, inviteeEmail,
				MembershipStatus.PENDING)) {
			throw new IllegalStateException("Bu e-postaya zaten bekleyen bir davet var");
		}

		Optional<User> existingUser = userRepository.findByEmail(inviteeEmail);
		if (existingUser.isPresent()) {
			boolean alreadyActive = membershipRepository.findByTenantIdAndUserIdAndStatus(organizationId,
					existingUser.get().getId(), MembershipStatus.ACTIVE).isPresent();
			if (alreadyActive) {
				throw new IllegalStateException("Bu kullanıcı zaten organizasyonun üyesi");
			}
		}

		Membership membership = Membership.invite(organizationId, inviteeEmail, role);
		membership = membershipRepository.save(membership);

		eventPublisher.publishEvent(new AuditEvent(organizationId, inviterUserId, "MEMBER_INVITED", "Membership",
				membership.getId(), Map.of("invitedEmail", inviteeEmail, "role", role.name())));

		// `organizations` tablosunda RLS yok (bkz. V1 migration), bu yüzden davet edilen kişi
		// henüz üye olmasa bile org adını burada okumak güvenli.
		String organizationName = organizationRepository.findById(organizationId)
				.map(Organization::getName)
				.orElse(organizationId.toString());
		emailService.sendInvitationEmail(inviteeEmail, organizationName, role);

		log.info("Üyelik daveti gönderildi: organizationId={}, invitedEmail={}, role={}, inviterUserId={}",
				organizationId, inviteeEmail, role, inviterUserId);

		return membership;
	}

	/** Org'un tüm üyelerini (PENDING davetler + ACTIVE üyeler) listeler — üye yönetimi ekranı için. */
	public Page<Membership> listOrganizationMembers(Pageable pageable) {
		UUID organizationId = TenantContext.get();
		return membershipRepository.findByTenantId(organizationId, pageable);
	}

	public List<Membership> listPendingInvitations(String email) {

		RlsSessionVariables.setUserEmail(entityManager, email);
		return membershipRepository.findByInvitedEmailAndStatus(email, MembershipStatus.PENDING);
	}

	@Transactional
	public Membership acceptInvitation(UUID membershipId, UUID acceptingUserId) {
		User acceptingUser = userRepository.findById(acceptingUserId)
				.orElseThrow(() -> new NotFoundException("Kullanıcı bulunamadı"));

		RlsSessionVariables.setUserEmail(entityManager, acceptingUser.getEmail());

		Membership membership = membershipRepository.findById(membershipId)
				.orElseThrow(() -> new NotFoundException("Davet bulunamadı"));

		membership.accept(acceptingUserId, acceptingUser.getEmail());

		eventPublisher.publishEvent(new AuditEvent(membership.getTenantId(), acceptingUserId, "INVITATION_ACCEPTED",
				"Membership", membership.getId(), null));

		log.info("Davet kabul edildi: membershipId={}, organizationId={}, userId={}", membershipId,
				membership.getTenantId(), acceptingUserId);

		return membership;
	}

	@Transactional
	public void rejectInvitation(UUID membershipId, UUID rejectingUserId) {
		User rejectingUser = userRepository.findById(rejectingUserId)
		        .orElseThrow(() -> new NotFoundException("Kullanıcı bulunamadı"));

		RlsSessionVariables.setUserEmail(entityManager, rejectingUser.getEmail());

		Membership membership = membershipRepository.findById(membershipId)
		        .orElseThrow(() -> new NotFoundException("Davet bulunamadı"));

		membership.reject(rejectingUser.getEmail());

		eventPublisher.publishEvent(new AuditEvent(membership.getTenantId(), rejectingUserId, "INVITATION_REJECTED",
				"Membership", membership.getId(), null));

		log.info("Davet reddedildi: membershipId={}, organizationId={}, userId={}", membershipId,
				membership.getTenantId(), rejectingUserId);
	}

	@Transactional
	public void revokeMembership(UUID organizationId, UUID actingUserId, UUID targetMembershipId) {
		requireAdminOrOwner(organizationId, actingUserId);

		Membership target = membershipRepository.findById(targetMembershipId)
		        .orElseThrow(() -> new NotFoundException("Üyelik bulunamadı"));

		if (target.isOwner()) {
			long activeOwnerCount = membershipRepository.countByTenantIdAndRoleAndStatus(organizationId, Role.OWNER,
					MembershipStatus.ACTIVE);
			if (activeOwnerCount <= 1) {
				log.warn(
						"Son OWNER kaldırma denemesi engellendi: organizationId={}, actingUserId={}, targetMembershipId={}",
						organizationId, actingUserId, targetMembershipId);
				throw new IllegalStateException("Organizasyonun son OWNER'ı kaldırılamaz");
			}
		}

		target.revoke();

		eventPublisher.publishEvent(new AuditEvent(organizationId, actingUserId, "MEMBERSHIP_REVOKED", "Membership",
				targetMembershipId, null));

		log.info("Üyelik iptal edildi: organizationId={}, targetMembershipId={}, actingUserId={}", organizationId,
				targetMembershipId, actingUserId);
	}

	@Transactional
	public void transferOwnership(UUID organizationId, UUID currentOwnerUserId, UUID newOwnerUserId) {
		if (currentOwnerUserId.equals(newOwnerUserId)) {
			throw new IllegalArgumentException("Sahiplik kendinize devredilemez");
		}
		RlsSessionVariables.setUserId(entityManager, currentOwnerUserId);
		Membership currentOwnerMembership = membershipRepository
				.lockByTenantIdAndUserIdAndStatus(organizationId, currentOwnerUserId, MembershipStatus.ACTIVE)
				.orElseThrow(() -> new AccessDeniedException("Bu organizasyonda üyeliğiniz yok"));

		if (!currentOwnerMembership.isOwner()) {
			log.warn("Yetkisiz sahiplik devri denemesi: organizationId={}, actingUserId={}", organizationId,
					currentOwnerUserId);
			throw new AccessDeniedException("Sadece mevcut OWNER sahipliği devredebilir");
		}

		Membership newOwnerMembership = membershipRepository
				.lockByTenantIdAndUserIdAndStatus(organizationId, newOwnerUserId, MembershipStatus.ACTIVE)
				.orElseThrow(() -> new IllegalArgumentException("Hedef kullanıcı bu organizasyonun aktif üyesi değil"));

		currentOwnerMembership.changeRole(Role.ADMIN);
		newOwnerMembership.changeRole(Role.OWNER);

		eventPublisher.publishEvent(new AuditEvent(organizationId, currentOwnerUserId, "OWNERSHIP_TRANSFERRED",
				"Organization", organizationId, Map.of("newOwnerUserId", newOwnerUserId.toString())));

		log.info("Sahiplik devredildi: organizationId={}, previousOwnerId={}, newOwnerId={}", organizationId,
				currentOwnerUserId, newOwnerUserId);
	}

	/** Davet edilen kullanıcı henüz üye olmadığı (PENDING) için `organizations`
	 * listesinde ismini göremez — burada organizasyon adını da ekliyoruz ki frontend "hangi
	 * organizasyon" sorusunu kullanıcıya UUID göstermeden cevaplayabilsin. `organizations`
	 * tablosunda RLS YOK (yalnızca alt tablolarda var, bkz. V1 migration) — bu yüzden henüz
	 * üye olunmayan bir organizasyonun adını okumak burada güvenli.
	 */
	@Transactional
	public List<PendingInvitationResponse> listMyPendingInvitations(UUID userId) {
		User user = userRepository.findById(userId)
		        .orElseThrow(() -> new NotFoundException("Kullanıcı bulunamadı"));
		List<Membership> pending = listPendingInvitations(user.getEmail());

		Map<UUID, String> orgNamesById = organizationRepository
				.findAllById(pending.stream().map(Membership::getTenantId).distinct().toList())
				.stream()
				.collect(Collectors.toMap(Organization::getId, Organization::getName));

		return pending.stream()
				.map(m -> new PendingInvitationResponse(m.getId(), m.getTenantId(),
						orgNamesById.get(m.getTenantId()), m.getRole()))
				.toList();
	}

	private void requireAdminOrOwner(UUID organizationId, UUID actorUserId) {
		membershipAuthorizationService.requireAdminOrOwner(organizationId, actorUserId);
	}
}