package com.AgentSaasAplication.runner.service;

import com.AgentSaasAplication.common.audit.AuditEvent;
import com.AgentSaasAplication.common.domain.TaskType;
import com.AgentSaasAplication.common.exceptions.NotFoundException;
import com.AgentSaasAplication.common.security.BridgeTokenGenerator;
import com.AgentSaasAplication.common.tenant.TenantContext;
import com.AgentSaasAplication.identity.service.MembershipAuthorizationService;
import com.AgentSaasAplication.project.service.ProjectService;
import com.AgentSaasAplication.runner.domain.RunnerCapability;
import com.AgentSaasAplication.runner.domain.RunnerConnection;
import com.AgentSaasAplication.runner.domain.RunnerStatus;
import com.AgentSaasAplication.runner.dto.RunnerConnectionResponse;
import com.AgentSaasAplication.runner.repository.RunnerCapabilityRepository;
import com.AgentSaasAplication.runner.repository.RunnerConnectionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional(readOnly = true)
public class RunnerConnectionService {

    private final RunnerConnectionRepository runnerConnectionRepository;
    private final RunnerCapabilityRepository runnerCapabilityRepository;
    private final MembershipAuthorizationService membershipAuthorizationService;
    private final ProjectService projectService;
    private final ApplicationEventPublisher eventPublisher;

    public RunnerConnectionService(RunnerConnectionRepository runnerConnectionRepository,
                                    RunnerCapabilityRepository runnerCapabilityRepository,
                                    MembershipAuthorizationService membershipAuthorizationService,
                                    ProjectService projectService,
                                    ApplicationEventPublisher eventPublisher) {
        this.runnerConnectionRepository = runnerConnectionRepository;
        this.runnerCapabilityRepository = runnerCapabilityRepository;
        this.membershipAuthorizationService = membershipAuthorizationService;
        this.projectService = projectService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Runner kaydı ADMIN/OWNER yetkisi gerektiriyor, ama sahibi kaydı yapan kişi olmak zorunda
     * değil — admin, çalışan adına runner tanımlayabiliyor. Aksi halde her runner admin'e ait
     * çıkar ve "görevi çalışana ata" akışı hiç çalışmaz.
     */
    @Transactional
    public RunnerConnection registerRunner(UUID actorUserId, UUID ownerUserId, UUID projectId,
                                            String label, List<TaskType> capabilities) {
        UUID organizationId = TenantContext.get();
        membershipAuthorizationService.requireAdminOrOwner(organizationId, actorUserId);

        UUID effectiveOwner = ownerUserId != null ? ownerUserId : actorUserId;
        if (ownerUserId != null) {
            membershipAuthorizationService.requireActiveMembership(
                    organizationId, ownerUserId, "Runner sahibi olarak belirtilen kullanıcı");
        }

        // Proje de tenant'a ait olmalı — RLS altında başka org'un projesi zaten görünmez.
        if (projectId != null) {
            projectService.ensureProjectExists(projectId);
        }

        RunnerConnection connection = runnerConnectionRepository.save(
                RunnerConnection.register(organizationId, effectiveOwner, projectId, label));

        for (TaskType taskType : capabilities) {
            runnerCapabilityRepository.save(
                    RunnerCapability.create(organizationId, connection.getId(), taskType));
        }

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("ownerUserId", effectiveOwner.toString());
        metadata.put("projectId", projectId == null ? "ALL" : projectId.toString());
        eventPublisher.publishEvent(new AuditEvent(
                organizationId, actorUserId, "RUNNER_REGISTERED", "RunnerConnection", connection.getId(), metadata));

        log.info("Runner kaydedildi: runnerId={}, organizationId={}, ownerUserId={}, projectId={}, capabilities={}",
                connection.getId(), organizationId, effectiveOwner, projectId, capabilities);

        return connection;
    }

    @Transactional
    public String issueBridgeToken(UUID actorUserId, UUID runnerConnectionId) {
        RunnerConnection connection = getRunnerConnection(runnerConnectionId);
        membershipAuthorizationService.requireAdminOrOwner(connection.getTenantId(), actorUserId);

        BridgeTokenGenerator.BridgeToken token = BridgeTokenGenerator.generate();
        connection.issueBridgeToken(token.hash());

        eventPublisher.publishEvent(new AuditEvent(
                connection.getTenantId(), actorUserId, "RUNNER_TOKEN_ISSUED", "RunnerConnection", runnerConnectionId,
                Map.of("ownerUserId", connection.getOwnerUserId().toString())));

        log.info("Runner bridge token üretildi: runnerId={}, organizationId={}, actorUserId={}",
                runnerConnectionId, connection.getTenantId(), actorUserId);

        return token.plaintext();
    }

    public RunnerConnection authenticateBridge(String plaintextToken) {
        UUID organizationId = TenantContext.get();
        String hash = BridgeTokenGenerator.hash(plaintextToken);

        return runnerConnectionRepository.findByTenantIdAndBridgeTokenHash(organizationId, hash)
                .orElseThrow(() -> new AccessDeniedException("Geçersiz bridge token"));
    }

    public RunnerConnection getRunnerConnection(UUID runnerConnectionId) {
        return runnerConnectionRepository.findById(runnerConnectionId)
                .orElseThrow(() -> new NotFoundException("Runner bağlantısı bulunamadı: " + runnerConnectionId));
    }

    public Page<RunnerConnection> listRunners(Pageable pageable) {
        return runnerConnectionRepository.findByTenantId(TenantContext.get(), pageable);
    }

    /** Sayfadaki runner'ların yeteneklerini TEK bir toplu sorguyla çekip bellekte gruplar.
     * Yanıt birleştirme servis katmanının sorumluluğudur (controller yalnızca HTTP'yi bilir)
     * ve satır başına ayrı sorgu atmak N+1 üretirdi. */
    public Page<RunnerConnectionResponse> listRunnersWithCapabilities(Pageable pageable) {
        Page<RunnerConnection> page = listRunners(pageable);
        List<UUID> runnerIds = page.getContent().stream().map(RunnerConnection::getId).toList();
        Map<UUID, List<TaskType>> capabilitiesByRunnerId = runnerCapabilityRepository.findByRunnerConnectionIdIn(runnerIds)
                .stream()
                .collect(Collectors.groupingBy(RunnerCapability::getRunnerConnectionId,
                        Collectors.mapping(RunnerCapability::getTaskType, Collectors.toList())));
        return page.map(connection ->
                RunnerConnectionResponse.from(connection, capabilitiesByRunnerId.getOrDefault(connection.getId(), List.of())));
    }

    public List<RunnerConnection> listOnlineRunners() {
        return runnerConnectionRepository.findByTenantIdAndStatus(TenantContext.get(), RunnerStatus.ONLINE);
    }

    public List<TaskType> getCapabilities(UUID runnerConnectionId) {
        return runnerCapabilityRepository.findByRunnerConnectionId(runnerConnectionId).stream()
                .map(RunnerCapability::getTaskType).toList();
    }

    public boolean hasCapability(UUID runnerConnectionId, TaskType taskType) {
        return runnerCapabilityRepository.existsByRunnerConnectionIdAndTaskType(runnerConnectionId, taskType);
    }

    @Transactional
    public void addCapability(UUID runnerConnectionId, TaskType taskType) {
        RunnerConnection connection = getRunnerConnection(runnerConnectionId);

        if (runnerCapabilityRepository.existsByRunnerConnectionIdAndTaskType(runnerConnectionId, taskType)) {
            throw new IllegalStateException("Bu runner zaten " + taskType + " yeteneğine sahip");
        }

        runnerCapabilityRepository.save(
                RunnerCapability.create(connection.getTenantId(), runnerConnectionId, taskType));
        log.info("Runner yeteneği eklendi: runnerId={}, taskType={}", runnerConnectionId, taskType);
    }

    @Transactional
    public void removeRunner(UUID actorUserId, UUID runnerConnectionId) {
        RunnerConnection connection = getRunnerConnection(runnerConnectionId);
        membershipAuthorizationService.requireAdminOrOwner(connection.getTenantId(), actorUserId);

        runnerCapabilityRepository.deleteByRunnerConnectionId(runnerConnectionId);
        runnerConnectionRepository.delete(connection);

        eventPublisher.publishEvent(new AuditEvent(
                connection.getTenantId(), actorUserId, "RUNNER_REMOVED", "RunnerConnection", runnerConnectionId, null));

        log.info("Runner kaldırıldı: runnerId={}, organizationId={}", runnerConnectionId, connection.getTenantId());
    }

    @Transactional
    public void recordHeartbeat(UUID runnerConnectionId) {
        getRunnerConnection(runnerConnectionId).recordHeartbeat();
    }

    @Transactional
    public void markOffline(UUID runnerConnectionId) {
        getRunnerConnection(runnerConnectionId).markOffline();
    }

    public List<UUID> findStaleOnlineRunnerIds(Instant staleBefore) {
        return runnerConnectionRepository
                .findByStatusAndLastHeartbeatAtBefore(RunnerStatus.ONLINE, staleBefore)
                .stream().map(RunnerConnection::getId).toList();
    }

    @Transactional
    public void markOfflineDueToStaleness(UUID runnerConnectionId) {
        RunnerConnection connection = getRunnerConnection(runnerConnectionId);
        if (connection.getStatus() != RunnerStatus.ONLINE) {
            return; // yarış durumu koruması — bu arada zaten offline olmuş olabilir
        }
        connection.markOffline();

        eventPublisher.publishEvent(new AuditEvent(
                connection.getTenantId(), null, "RUNNER_MARKED_STALE", "RunnerConnection", runnerConnectionId,
                Map.of("lastHeartbeatAt", String.valueOf(connection.getLastHeartbeatAt()))));

        log.warn("Runner heartbeat zaman aşımı, offline işaretlendi: runnerId={}, organizationId={}",
                runnerConnectionId, connection.getTenantId());
    }
}
