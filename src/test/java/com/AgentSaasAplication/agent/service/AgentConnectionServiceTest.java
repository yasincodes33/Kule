package com.AgentSaasAplication.agent.service;

import com.AgentSaasAplication.agent.connector.ApiKeyVerifier;
import com.AgentSaasAplication.agent.connector.ApiKeyVerifierRegistry;
import com.AgentSaasAplication.agent.domain.AgentCapability;
import com.AgentSaasAplication.agent.domain.AgentConnection;
import com.AgentSaasAplication.agent.domain.AgentType;
import com.AgentSaasAplication.agent.dto.AgentConnectionResponse;
import com.AgentSaasAplication.agent.repository.AgentCapabilityRepository;
import com.AgentSaasAplication.agent.repository.AgentConnectionRepository;
import com.AgentSaasAplication.common.domain.TaskType;
import com.AgentSaasAplication.common.security.ApiKeyCipher;
import com.AgentSaasAplication.common.tenant.TenantContext;
import com.AgentSaasAplication.identity.service.MembershipAuthorizationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * registerAgent()'ın API key doğrulama zinciri — kritik model erişilemezse (yanlış
 * anahtar) kaydın engellenmesi, ama ikincil bir model kademesi erişilemezse (ör. sadece
 * reasoning kademesi kapalı) kaydın YİNE DE oluşması gereken iki farklı davranış.
 */
@ExtendWith(MockitoExtension.class)
class AgentConnectionServiceTest {

    @Mock private AgentConnectionRepository agentConnectionRepository;
    @Mock private AgentCapabilityRepository agentCapabilityRepository;
    @Mock private MembershipAuthorizationService membershipAuthorizationService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private ApiKeyVerifierRegistry apiKeyVerifierRegistry;
    @Mock private ApiKeyCipher apiKeyCipher;
    @Mock private ApiKeyVerifier apiKeyVerifier;

    private AgentConnectionService service;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID actorUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new AgentConnectionService(agentConnectionRepository, agentCapabilityRepository,
                membershipAuthorizationService, eventPublisher, apiKeyVerifierRegistry, apiKeyCipher);
        TenantContext.set(organizationId);
        lenient().when(agentConnectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(apiKeyCipher.encrypt(any())).thenReturn("encrypted");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void ayni_tipte_ikinci_agent_kaydi_reddedilir() {
        when(agentConnectionRepository.existsByTenantIdAndAgentType(organizationId, AgentType.CLAUDE)).thenReturn(true);

        assertThatThrownBy(() -> service.registerAgent(actorUserId, AgentType.CLAUDE, List.of(), "key"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("zaten bir CLAUDE agent'ı kayıtlı");
    }

    @Test
    void bos_api_key_reddedilir() {
        when(agentConnectionRepository.existsByTenantIdAndAgentType(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.registerAgent(actorUserId, AgentType.CLAUDE, List.of(), "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("API key zorunlu");
    }

    @Test
    void bu_agent_tipi_icin_verifier_yoksa_illegal_state_firlatir() {
        when(agentConnectionRepository.existsByTenantIdAndAgentType(any(), any())).thenReturn(false);
        when(apiKeyVerifierRegistry.find(AgentType.CLAUDE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.registerAgent(actorUserId, AgentType.CLAUDE, List.of(), "key"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("doğrulayıcı tanımlı değil");
    }

    @Test
    void kritik_model_erisilemezse_kayit_reddedilir() {
        when(agentConnectionRepository.existsByTenantIdAndAgentType(any(), any())).thenReturn(false);
        when(apiKeyVerifierRegistry.find(AgentType.CLAUDE)).thenReturn(Optional.of(apiKeyVerifier));
        when(apiKeyVerifier.verifyModels("yanlış-key"))
                .thenReturn(new ApiKeyVerifier.ModelVerificationResult(false, List.of("claude-default")));

        assertThatThrownBy(() -> service.registerAgent(actorUserId, AgentType.CLAUDE, List.of(), "yanlış-key"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("doğrulanamadı");

        verify(agentConnectionRepository, never()).save(any());
    }

    @Test
    void kritik_model_calisiyorsa_ikincil_kademe_erisilemez_olsa_bile_kayit_olusur() {
        when(agentConnectionRepository.existsByTenantIdAndAgentType(any(), any())).thenReturn(false);
        when(apiKeyVerifierRegistry.find(AgentType.CLAUDE)).thenReturn(Optional.of(apiKeyVerifier));
        when(apiKeyVerifier.verifyModels("key"))
                .thenReturn(new ApiKeyVerifier.ModelVerificationResult(true, List.of("claude-reasoning")));

        AgentConnection result = service.registerAgent(actorUserId, AgentType.CLAUDE, List.of(TaskType.DEV), "key");

        assertThat(result.getAgentType()).isEqualTo(AgentType.CLAUDE);
        verify(agentConnectionRepository).save(any());
        verify(agentCapabilityRepository).save(any());
        verify(eventPublisher).publishEvent(any(com.AgentSaasAplication.common.audit.AuditEvent.class));
    }

    @Test
    void ayni_yetenegi_ikinci_kez_eklemek_reddedilir() {
        UUID connectionId = UUID.randomUUID();
        AgentConnection connection = AgentConnection.register(organizationId, AgentType.CLAUDE, "encrypted");
        when(agentConnectionRepository.findById(connectionId)).thenReturn(Optional.of(connection));
        when(agentCapabilityRepository.existsByAgentConnectionIdAndTaskType(connectionId, TaskType.DEV)).thenReturn(true);

        assertThatThrownBy(() -> service.addCapability(connectionId, TaskType.DEV))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("zaten");
    }

    @Test
    void listAgentConnectionsWithCapabilities_her_agent_icin_tek_toplu_sorguyla_yetenekleri_dogru_esler() {
        // Sayfadaki tüm agent'ların yetenekleri tek bir toplu sorguyla çekilir; agent başına ayrı sorgu
        // controller'da atılıyordu — bu test TEK toplu sorgu (findByAgentConnectionIdIn) ile
        // doğru eşlemeyi kanıtlıyor.
        AgentConnection connectionA = AgentConnection.register(organizationId, AgentType.CLAUDE, "enc-a");
        AgentConnection connectionB = AgentConnection.register(organizationId, AgentType.CHATGPT, "enc-b");
        UUID idA = UUID.randomUUID();
        UUID idB = UUID.randomUUID();
        ReflectionTestUtils.setField(connectionA, "id", idA);
        ReflectionTestUtils.setField(connectionB, "id", idB);
        Page<AgentConnection> page = new PageImpl<>(List.of(connectionA, connectionB), PageRequest.of(0, 20), 2);
        when(agentConnectionRepository.findByTenantId(organizationId, PageRequest.of(0, 20))).thenReturn(page);
        when(agentCapabilityRepository.findByAgentConnectionIdIn(List.of(idA, idB))).thenReturn(List.of(
                AgentCapability.create(organizationId, idA, TaskType.DEV)));

        Page<AgentConnectionResponse> result = service.listAgentConnectionsWithCapabilities(PageRequest.of(0, 20));

        AgentConnectionResponse responseA = result.getContent().stream().filter(r -> r.id().equals(idA)).findFirst().orElseThrow();
        AgentConnectionResponse responseB = result.getContent().stream().filter(r -> r.id().equals(idB)).findFirst().orElseThrow();
        assertThat(responseA.capabilities()).containsExactly(TaskType.DEV);
        assertThat(responseB.capabilities()).isEmpty();
        verify(agentCapabilityRepository, never()).findByAgentConnectionId(any());
    }
}
