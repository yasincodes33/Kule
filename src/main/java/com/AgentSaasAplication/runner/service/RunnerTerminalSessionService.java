package com.AgentSaasAplication.runner.service;

import com.AgentSaasAplication.approval.domain.ApprovalStatus;
import com.AgentSaasAplication.common.audit.AuditEvent;
import com.AgentSaasAplication.common.exceptions.NotFoundException;
import com.AgentSaasAplication.common.tenant.TenantContext;
import com.AgentSaasAplication.identity.service.MembershipAuthorizationService;
import com.AgentSaasAplication.runner.domain.RunnerConnection;
import com.AgentSaasAplication.runner.domain.RunnerTerminalSessionRequest;
import com.AgentSaasAplication.runner.repository.RunnerTerminalSessionRequestRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * ApprovalService'in aynı şekli — RunnerTerminalSessionRequest, ApprovalRequest'in task'tan
 * bağımsız bir kardeşi (bkz. RunnerTerminalSessionRequest Javadoc'u). Onaylayan kişi burada
 * task_id/taskStateService.transition() ile hiç ilgilenmiyor, çünkü onaylanan şey bir görev
 * değil, kullanıcının kendi runner'ına canlı bir shell oturumu açma yetkisi.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class RunnerTerminalSessionService {

    /** Onaylanmış bir isteğin ne kadar süre "bağlanabilir" kalacağı — task onaylarındaki 24 saatten
     * kasıtlı olarak çok daha kısa: bu, süresiz geçerli bir shell erişim yetkisi değil. */
    private static final Duration REQUEST_TTL = Duration.ofMinutes(15);

    private final RunnerTerminalSessionRequestRepository repository;
    private final RunnerConnectionService runnerConnectionService;
    private final MembershipAuthorizationService membershipAuthorizationService;
    private final ApplicationEventPublisher eventPublisher;

    public RunnerTerminalSessionService(RunnerTerminalSessionRequestRepository repository,
                                         RunnerConnectionService runnerConnectionService,
                                         MembershipAuthorizationService membershipAuthorizationService,
                                         ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.runnerConnectionService = runnerConnectionService;
        this.membershipAuthorizationService = membershipAuthorizationService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public RunnerTerminalSessionRequest requestSession(UUID runnerConnectionId, UUID requestedByUserId) {
        UUID organizationId = TenantContext.get();

        RunnerConnection connection = runnerConnectionService.getRunnerConnection(runnerConnectionId);
        if (!connection.isOnline()) {
            throw new IllegalStateException("Runner şu anda çevrimdışı, terminal oturumu istenemez");
        }
        if (repository.existsByRunnerConnectionIdAndStatus(runnerConnectionId, ApprovalStatus.PENDING)) {
            throw new IllegalStateException("Bu runner için zaten bekleyen bir terminal isteği var");
        }

        Instant expiresAt = Instant.now().plus(REQUEST_TTL);
        RunnerTerminalSessionRequest request = RunnerTerminalSessionRequest.requestFor(
                organizationId, runnerConnectionId, requestedByUserId, expiresAt);
        request = repository.save(request);

        eventPublisher.publishEvent(new AuditEvent(
                organizationId, requestedByUserId, "TERMINAL_SESSION_REQUESTED", "RunnerTerminalSessionRequest",
                request.getId(), Map.of("runnerConnectionId", runnerConnectionId.toString())));

        log.info("Terminal oturum isteği oluşturuldu: requestId={}, runnerConnectionId={}, expiresAt={}",
                request.getId(), runnerConnectionId, expiresAt);
        return request;
    }

    @Transactional
    public RunnerTerminalSessionRequest approve(UUID requestId, UUID approverUserId) {
        UUID organizationId = TenantContext.get();
        membershipAuthorizationService.requireApprovalPermission(organizationId, approverUserId);

        RunnerTerminalSessionRequest request = getSession(requestId);
        request.approve(approverUserId);

        eventPublisher.publishEvent(new AuditEvent(
                organizationId, approverUserId, "TERMINAL_SESSION_APPROVED", "RunnerTerminalSessionRequest",
                requestId, Map.of("runnerConnectionId", request.getRunnerConnectionId().toString())));

        log.info("Terminal oturum isteği onaylandı: requestId={}, approverId={}", requestId, approverUserId);
        return request;
    }

    @Transactional
    public RunnerTerminalSessionRequest reject(UUID requestId, UUID approverUserId) {
        UUID organizationId = TenantContext.get();
        membershipAuthorizationService.requireApprovalPermission(organizationId, approverUserId);

        RunnerTerminalSessionRequest request = getSession(requestId);
        request.reject(approverUserId);

        eventPublisher.publishEvent(new AuditEvent(
                organizationId, approverUserId, "TERMINAL_SESSION_REJECTED", "RunnerTerminalSessionRequest",
                requestId, Map.of("runnerConnectionId", request.getRunnerConnectionId().toString())));

        log.info("Terminal oturum isteği reddedildi: requestId={}, approverId={}", requestId, approverUserId);
        return request;
    }

    public RunnerTerminalSessionRequest getSession(UUID requestId) {
        return repository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("Terminal oturum isteği bulunamadı: " + requestId));
    }

    /** ApprovalService.listPendingApprovals'ın aynı şekli — onaylayıcıların RunnersPage'de
     * bekleyen terminal isteklerini görebilmesi için. */
    public Page<RunnerTerminalSessionRequest> listPendingSessions(Pageable pageable) {
        return repository.findByTenantIdAndStatus(TenantContext.get(), ApprovalStatus.PENDING, pageable);
    }

    /** WS bileti üretmeden hemen önce: istek onaylı mı, isteyen kullanıcıya mı ait, süresi geçmemiş mi. */
    public RunnerTerminalSessionRequest requireUsableApprovedSession(UUID requestId, UUID userId) {
        RunnerTerminalSessionRequest request = getSession(requestId);
        if (!request.getRequestedBy().equals(userId)) {
            throw new AccessDeniedException("Bu terminal oturumu size ait değil");
        }
        if (request.getStatus() != ApprovalStatus.APPROVED) {
            throw new IllegalStateException("Terminal oturumu henüz onaylanmadı: " + request.getStatus());
        }
        if (request.isExpired()) {
            throw new IllegalStateException("Terminal oturum isteğinin süresi doldu");
        }
        return request;
    }
}
