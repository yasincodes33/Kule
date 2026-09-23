package com.AgentSaasAplication.agent.service;

import com.AgentSaasAplication.agent.connector.ApiKeyVerifier;
import com.AgentSaasAplication.agent.connector.ApiKeyVerifierRegistry;
import com.AgentSaasAplication.agent.domain.AgentCapability;
import com.AgentSaasAplication.agent.domain.AgentConnection;
import com.AgentSaasAplication.agent.domain.AgentStatus;
import com.AgentSaasAplication.agent.domain.AgentType;
import com.AgentSaasAplication.agent.dto.AgentConnectionResponse;
import com.AgentSaasAplication.agent.repository.AgentCapabilityRepository;
import com.AgentSaasAplication.agent.repository.AgentConnectionRepository;
import com.AgentSaasAplication.common.audit.AuditEvent;
import com.AgentSaasAplication.common.domain.TaskType;
import com.AgentSaasAplication.common.exceptions.NotFoundException;
import com.AgentSaasAplication.common.security.ApiKeyCipher;
import com.AgentSaasAplication.common.tenant.TenantContext;
import com.AgentSaasAplication.identity.service.MembershipAuthorizationService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Bulut model sağlayıcısı kayıtlarını yönetir (Claude/ChatGPT/Gemini).
 * Yerel runner'lar için {@link com.AgentSaasAplication.runner.service.RunnerConnectionService}.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class AgentConnectionService {

    private final AgentConnectionRepository agentConnectionRepository;
    private final AgentCapabilityRepository agentCapabilityRepository;
    private final MembershipAuthorizationService membershipAuthorizationService;
    private final ApplicationEventPublisher eventPublisher;
    private final ApiKeyVerifierRegistry apiKeyVerifierRegistry;
    private final ApiKeyCipher apiKeyCipher;

    public AgentConnectionService(AgentConnectionRepository agentConnectionRepository,
            AgentCapabilityRepository agentCapabilityRepository,
            MembershipAuthorizationService membershipAuthorizationService,
            ApplicationEventPublisher eventPublisher,
            ApiKeyVerifierRegistry apiKeyVerifierRegistry,
            ApiKeyCipher apiKeyCipher) {
        this.agentConnectionRepository = agentConnectionRepository;
        this.agentCapabilityRepository = agentCapabilityRepository;
        this.membershipAuthorizationService = membershipAuthorizationService;
        this.eventPublisher = eventPublisher;
        this.apiKeyVerifierRegistry = apiKeyVerifierRegistry;
        this.apiKeyCipher = apiKeyCipher;
    }

    @Transactional
    public AgentConnection registerAgent(UUID actorUserId, AgentType agentType, List<TaskType> capabilities, String apiKey) {
        UUID organizationId = TenantContext.get();
        membershipAuthorizationService.requireAdminOrOwner(organizationId, actorUserId);

        // Aynı org'da iki Claude kaydı = iki farklı API anahtarı, hangisinin kullanılacağı
        // tanımsız olurdu. Runner'lar için böyle bir kısıt YOK (her çalışanın kendi makinesi var).
        if (agentConnectionRepository.existsByTenantIdAndAgentType(organizationId, agentType)) {
            throw new IllegalStateException("Bu organizasyonda zaten bir " + agentType + " agent'ı kayıtlı");
        }

        AgentConnection connection = agentConnectionRepository.save(
                buildVerifiedConnection(organizationId, agentType, apiKey));

        for (TaskType taskType : capabilities) {
            agentCapabilityRepository.save(AgentCapability.create(organizationId, connection.getId(), taskType));
        }

        eventPublisher.publishEvent(new AuditEvent(
                organizationId, actorUserId, "AGENT_REGISTERED", "AgentConnection", connection.getId(),
                Map.of("agentType", agentType.name())));

        log.info("Agent kaydedildi: connectionId={}, organizationId={}, type={}, capabilities={}, actorUserId={}",
                connection.getId(), organizationId, agentType, capabilities, actorUserId);

        return connection;
    }

    private AgentConnection buildVerifiedConnection(UUID organizationId, AgentType agentType, String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException(agentType + " için API key zorunlu");
        }

        ApiKeyVerifier verifier = apiKeyVerifierRegistry.find(agentType)
                .orElseThrow(() -> new IllegalStateException("Bu agent tipi için doğrulayıcı tanımlı değil: " + agentType));

        ApiKeyVerifier.ModelVerificationResult result = verifier.verifyModels(apiKey);

        if (!result.criticalModelOk()) {
            // Nedeni de söylüyoruz: yanlış anahtar (401), yapılandırmadaki model adının artık
            // var olmaması (404) ve ağa çıkamamak çok farklı sorunlar — kullanıcı hangisi
            // olduğunu bilmeden kaydı ekleyemiyordu.
            throw new IllegalArgumentException(
                    "API key doğrulanamadı — temel model (" + agentType + " default) erişilebilir değil"
                            + (result.detail() != null ? " [" + result.detail() + "]" : ""));
        }
        if (!result.failedModels().isEmpty()) {
            log.warn("Agent kaydedildi ama bazı model kademelerine erişim yok: agentType={}, erişilemeyen={}",
                    agentType, result.failedModels());
        }

        return AgentConnection.register(organizationId, agentType, apiKeyCipher.encrypt(apiKey));
    }

    public Page<AgentConnection> listAgentConnections(Pageable pageable) {
        return agentConnectionRepository.findByTenantId(TenantContext.get(), pageable);
    }

    /** Sayfadaki agent'ların yeteneklerini TEK bir toplu sorguyla çeker; satır başına
     * ayrı bir sorgu atılmaz (N+1). */
    public Page<AgentConnectionResponse> listAgentConnectionsWithCapabilities(Pageable pageable) {
        Page<AgentConnection> page = listAgentConnections(pageable);
        List<UUID> connectionIds = page.getContent().stream().map(AgentConnection::getId).toList();
        Map<UUID, List<TaskType>> capabilitiesByConnectionId = agentCapabilityRepository.findByAgentConnectionIdIn(connectionIds)
                .stream()
                .collect(Collectors.groupingBy(AgentCapability::getAgentConnectionId,
                        Collectors.mapping(AgentCapability::getTaskType, Collectors.toList())));
        return page.map(connection ->
                AgentConnectionResponse.from(connection, capabilitiesByConnectionId.getOrDefault(connection.getId(), List.of())));
    }

    public AgentConnection getAgentConnection(UUID connectionId) {
        return agentConnectionRepository.findById(connectionId)
                .orElseThrow(() -> new NotFoundException("Agent bağlantısı bulunamadı: " + connectionId));
    }

    public List<AgentCapability> getCapabilities(UUID connectionId) {
        return agentCapabilityRepository.findByAgentConnectionId(connectionId);
    }

    public List<AgentConnection> listOnlineAgents() {
        return agentConnectionRepository.findByTenantIdAndStatus(TenantContext.get(), AgentStatus.ONLINE);
    }

    public boolean hasCapability(UUID connectionId, TaskType taskType) {
        return agentCapabilityRepository.existsByAgentConnectionIdAndTaskType(connectionId, taskType);
    }

    @Transactional
    public void removeAgent(UUID actorUserId, UUID connectionId) {
        AgentConnection connection = getAgentConnection(connectionId);
        membershipAuthorizationService.requireAdminOrOwner(connection.getTenantId(), actorUserId);

        agentCapabilityRepository.deleteByAgentConnectionId(connectionId);
        agentConnectionRepository.delete(connection);

        eventPublisher.publishEvent(new AuditEvent(
                connection.getTenantId(), actorUserId, "AGENT_REMOVED", "AgentConnection", connectionId,
                Map.of("agentType", connection.getAgentType().name())));

        log.info("Agent kaldırıldı: connectionId={}, organizationId={}, type={}, actorUserId={}",
                connectionId, connection.getTenantId(), connection.getAgentType(), actorUserId);
    }

    @Transactional
    public void addCapability(UUID connectionId, TaskType taskType) {
        AgentConnection connection = getAgentConnection(connectionId);

        if (agentCapabilityRepository.existsByAgentConnectionIdAndTaskType(connectionId, taskType)) {
            throw new IllegalStateException("Bu agent zaten " + taskType + " yeteneğine sahip");
        }

        agentCapabilityRepository.save(AgentCapability.create(connection.getTenantId(), connectionId, taskType));
        log.info("Agent yeteneği eklendi: connectionId={}, taskType={}", connectionId, taskType);
    }
}
