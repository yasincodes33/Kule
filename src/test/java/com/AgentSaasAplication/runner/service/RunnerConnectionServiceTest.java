package com.AgentSaasAplication.runner.service;

import com.AgentSaasAplication.common.domain.TaskType;
import com.AgentSaasAplication.common.exceptions.NotFoundException;
import com.AgentSaasAplication.common.tenant.TenantContext;
import com.AgentSaasAplication.identity.service.MembershipAuthorizationService;
import com.AgentSaasAplication.project.service.ProjectService;
import com.AgentSaasAplication.runner.domain.RunnerCapability;
import com.AgentSaasAplication.runner.domain.RunnerConnection;
import com.AgentSaasAplication.runner.dto.RunnerConnectionResponse;
import com.AgentSaasAplication.runner.repository.RunnerCapabilityRepository;
import com.AgentSaasAplication.runner.repository.RunnerConnectionRepository;
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
 * registerRunner()'ın "admin başka bir çalışan adına runner kaydedebilir" ve
 * markOfflineDueToStaleness()'ın yarış-durumu koruması — RunnerHealthScheduler eşzamanlı
 * çalışırken zaten OFFLINE olmuş bir runner'ı tekrar OFFLINE'a çekip gereksiz AuditEvent
 * üretmemesi için kritik.
 */
@ExtendWith(MockitoExtension.class)
class RunnerConnectionServiceTest {

    @Mock private RunnerConnectionRepository runnerConnectionRepository;
    @Mock private RunnerCapabilityRepository runnerCapabilityRepository;
    @Mock private MembershipAuthorizationService membershipAuthorizationService;
    @Mock private ProjectService projectService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private RunnerConnectionService service;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID actorUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new RunnerConnectionService(runnerConnectionRepository, runnerCapabilityRepository,
                membershipAuthorizationService, projectService, eventPublisher);
        TenantContext.set(organizationId);
        lenient().when(runnerConnectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void ownerUserId_verilmezse_kayit_isteyen_kisi_sahip_olur() {
        RunnerConnection result = service.registerRunner(actorUserId, null, null, "ev", List.of());

        assertThat(result.getOwnerUserId()).isEqualTo(actorUserId);
        verify(membershipAuthorizationService, never()).requireActiveMembership(any(), any(), any());
    }

    @Test
    void ownerUserId_baska_bir_calisansa_org_uyeligi_dogrulanir() {
        UUID ownerUserId = UUID.randomUUID();

        RunnerConnection result = service.registerRunner(actorUserId, ownerUserId, null, "ev", List.of());

        assertThat(result.getOwnerUserId()).isEqualTo(ownerUserId);
        verify(membershipAuthorizationService).requireActiveMembership(
                organizationId, ownerUserId, "Runner sahibi olarak belirtilen kullanıcı");
    }

    @Test
    void ownerUserId_org_uyesi_degilse_kayit_basarisiz_olur() {
        UUID ownerUserId = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new IllegalArgumentException("aktif üyesi değil"))
                .when(membershipAuthorizationService)
                .requireActiveMembership(organizationId, ownerUserId, "Runner sahibi olarak belirtilen kullanıcı");

        assertThatThrownBy(() -> service.registerRunner(actorUserId, ownerUserId, null, "ev", List.of()))
                .isInstanceOf(IllegalArgumentException.class);

        verify(runnerConnectionRepository, never()).save(any());
    }

    @Test
    void var_olmayan_projeye_kayit_notfound_firlatir() {
        UUID projectId = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new NotFoundException("Proje bulunamadı veya yetkiniz yok: " + projectId))
                .when(projectService).ensureProjectExists(projectId);

        assertThatThrownBy(() -> service.registerRunner(actorUserId, null, projectId, "ev", List.of()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void capabilities_listesindeki_her_tip_icin_ayri_kayit_olusturulur() {
        service.registerRunner(actorUserId, null, null, "ev", List.of(TaskType.DEV, TaskType.ANALYSIS));

        verify(runnerCapabilityRepository, org.mockito.Mockito.times(2)).save(any());
    }

    @Test
    void ayni_yetenegi_ikinci_kez_eklemek_reddedilir() {
        UUID runnerId = UUID.randomUUID();
        RunnerConnection runner = RunnerConnection.register(organizationId, actorUserId, null, "ev");
        when(runnerConnectionRepository.findById(runnerId)).thenReturn(Optional.of(runner));
        when(runnerCapabilityRepository.existsByRunnerConnectionIdAndTaskType(runnerId, TaskType.DEV)).thenReturn(true);

        assertThatThrownBy(() -> service.addCapability(runnerId, TaskType.DEV))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("zaten");
    }

    @Test
    void hala_online_olan_runner_staleness_yuzunden_offline_yapilir_ve_denetim_olayi_yayinlanir() {
        UUID runnerId = UUID.randomUUID();
        RunnerConnection runner = RunnerConnection.register(organizationId, actorUserId, null, "ev");
        runner.recordHeartbeat();
        when(runnerConnectionRepository.findById(runnerId)).thenReturn(Optional.of(runner));

        service.markOfflineDueToStaleness(runnerId);

        assertThat(runner.isOnline()).isFalse();
        verify(eventPublisher).publishEvent(any(com.AgentSaasAplication.common.audit.AuditEvent.class));
    }

    @Test
    void zaten_offline_olan_runner_icin_yarisi_durumu_korumasi_devreye_girer_olay_yayinlanmaz() {
        UUID runnerId = UUID.randomUUID();
        RunnerConnection runner = RunnerConnection.register(organizationId, actorUserId, null, "ev"); // zaten OFFLINE
        when(runnerConnectionRepository.findById(runnerId)).thenReturn(Optional.of(runner));

        service.markOfflineDueToStaleness(runnerId);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void hasCapability_repository_sonucunu_dogrudan_yansitir() {
        UUID runnerId = UUID.randomUUID();
        when(runnerCapabilityRepository.existsByRunnerConnectionIdAndTaskType(runnerId, TaskType.DEPLOY)).thenReturn(true);

        assertThat(service.hasCapability(runnerId, TaskType.DEPLOY)).isTrue();
    }

    @Test
    void listRunnersWithCapabilities_her_runner_icin_tek_toplu_sorguyla_yetenekleri_dogru_esler() {
        // Sayfadaki tüm runner'ların yetenekleri tek bir toplu sorguyla çekilir; runner başına ayrı sorgu
        // atılıyordu — bu test, sayfadaki İKİ runner için TEK toplu sorgu (findByRunnerConnectionIdIn)
        // atıldığını ve dönen capability'lerin doğru runner'a eşlendiğini kanıtlıyor.
        RunnerConnection runnerA = RunnerConnection.register(organizationId, actorUserId, null, "a");
        RunnerConnection runnerB = RunnerConnection.register(organizationId, actorUserId, null, "b");
        UUID idA = UUID.randomUUID();
        UUID idB = UUID.randomUUID();
        ReflectionTestUtils.setField(runnerA, "id", idA);
        ReflectionTestUtils.setField(runnerB, "id", idB);
        Page<RunnerConnection> page = new PageImpl<>(List.of(runnerA, runnerB), PageRequest.of(0, 20), 2);
        when(runnerConnectionRepository.findByTenantId(organizationId, PageRequest.of(0, 20))).thenReturn(page);
        when(runnerCapabilityRepository.findByRunnerConnectionIdIn(List.of(idA, idB))).thenReturn(List.of(
                RunnerCapability.create(organizationId, idA, TaskType.DEPLOY),
                RunnerCapability.create(organizationId, idA, TaskType.DEV)));

        Page<RunnerConnectionResponse> result = service.listRunnersWithCapabilities(PageRequest.of(0, 20));

        RunnerConnectionResponse responseA = result.getContent().stream().filter(r -> r.id().equals(idA)).findFirst().orElseThrow();
        RunnerConnectionResponse responseB = result.getContent().stream().filter(r -> r.id().equals(idB)).findFirst().orElseThrow();
        assertThat(responseA.capabilities()).containsExactlyInAnyOrder(TaskType.DEPLOY, TaskType.DEV);
        assertThat(responseB.capabilities()).isEmpty();
        verify(runnerCapabilityRepository, never()).findByRunnerConnectionId(any());
    }
}
