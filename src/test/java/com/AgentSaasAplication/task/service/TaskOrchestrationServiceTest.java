package com.AgentSaasAplication.task.service;

import com.AgentSaasAplication.agent.domain.AgentConnection;
import com.AgentSaasAplication.agent.domain.AgentType;
import com.AgentSaasAplication.agent.service.AgentConnectionService;
import com.AgentSaasAplication.common.domain.TaskType;
import com.AgentSaasAplication.common.exceptions.NotFoundException;
import com.AgentSaasAplication.common.tenant.TenantContext;
import com.AgentSaasAplication.identity.service.MembershipAuthorizationService;
import com.AgentSaasAplication.project.service.ProjectService;
import com.AgentSaasAplication.runner.domain.RunnerConnection;
import com.AgentSaasAplication.runner.service.RunnerConnectionService;
import com.AgentSaasAplication.task.domain.Task;
import com.AgentSaasAplication.task.domain.TaskStatus;
import com.AgentSaasAplication.task.repository.TaskCreatedEventOutboxRepository;
import com.AgentSaasAplication.task.repository.TaskLogRepository;
import com.AgentSaasAplication.task.repository.TaskRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TaskOrchestrationService.createTask(), Faz 4/5 denetiminde bulunan IDOR benzeri
 * açığın (assignedUserId hiç doğrulanmıyordu) düzeltildiği yer — bu testler o doğrulamanın ve
 * "agent+runner aynı anda verilemez" fail-fast kuralının regresyona uğramadığından emin olur.
 */
@ExtendWith(MockitoExtension.class)
class TaskOrchestrationServiceTest {

    @Mock private TaskRepository taskRepository;
    @Mock private TaskLogRepository taskLogRepository;
    @Mock private TaskCreatedEventOutboxRepository taskCreatedEventOutboxRepository;
    @Mock private TaskStateService taskStateService;
    @Mock private AgentConnectionService agentConnectionService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private ProjectService projectService;
    @Mock private MembershipAuthorizationService membershipAuthorizationService;
    @Mock private RunnerConnectionService runnerConnectionService;

    private TaskOrchestrationService service;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();
    private final UUID actorUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new TaskOrchestrationService(taskRepository, taskLogRepository, taskCreatedEventOutboxRepository,
                taskStateService, agentConnectionService, eventPublisher, projectService,
                membershipAuthorizationService, runnerConnectionService);
        TenantContext.set(organizationId);
        lenient().when(taskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private void projectExists() {
        // ensureProjectExists void olduğu için varsayılan mock davranışı (hiçbir şey atmamak)
        // zaten "proje var" anlamına geliyor — açık bir stub gerekmiyor, yalnızca okunabilirlik için.
    }

    @Test
    void proje_bulunamazsa_notfound_firlatir() {
        org.mockito.Mockito.doThrow(new NotFoundException("Proje bulunamadı veya yetkiniz yok: " + projectId))
                .when(projectService).ensureProjectExists(projectId);

        assertThatThrownBy(() -> service.createTask(
                actorUserId, projectId, TaskType.DEV, "başlık", null, null, null, null))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void agent_ve_runner_ayni_anda_verilirse_illegal_argument_firlatir() {
        projectExists();
        UUID agentId = UUID.randomUUID();
        UUID runnerId = UUID.randomUUID();

        assertThatThrownBy(() -> service.createTask(
                actorUserId, projectId, TaskType.DEV, "başlık", agentId, runnerId, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("aynı anda verilemez");

        verify(taskRepository, never()).save(any());
    }

    @Test
    void assignedUserId_org_uyesi_degilse_gorev_olusturulamaz() {
        projectExists();
        UUID assignedUserId = UUID.randomUUID();
        doThrow(new IllegalArgumentException("aktif üyesi değil"))
                .when(membershipAuthorizationService)
                .requireActiveMembership(organizationId, assignedUserId, "Görevin atandığı kullanıcı");

        assertThatThrownBy(() -> service.createTask(
                actorUserId, projectId, TaskType.DEV, "başlık", null, null, null, assignedUserId))
                .isInstanceOf(IllegalArgumentException.class);

        verify(taskRepository, never()).save(any());
    }

    @Test
    void secilen_agent_gorev_tipini_desteklemiyorsa_reddedilir() {
        projectExists();
        UUID agentId = UUID.randomUUID();
        // AgentConnection.register(...) hiç save edilmediği için getId() null döner — servis
        // preferred.getId()'yi (agentId parametresinin kendisini değil) kullandığından stub da
        // null ile eşleşmeli.
        AgentConnection agent = AgentConnection.register(organizationId, AgentType.CLAUDE, "encrypted");
        when(agentConnectionService.getAgentConnection(agentId)).thenReturn(agent);
        when(agentConnectionService.hasCapability(null, TaskType.DEPLOY)).thenReturn(false);

        assertThatThrownBy(() -> service.createTask(
                actorUserId, projectId, TaskType.DEPLOY, "başlık", agentId, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("desteklemiyor");
    }

    @Test
    void secilen_runner_var_olmayan_bir_kayitsa_notfound_firlatir() {
        projectExists();
        UUID runnerId = UUID.randomUUID();
        when(runnerConnectionService.getRunnerConnection(runnerId))
                .thenThrow(new NotFoundException("Runner bağlantısı bulunamadı: " + runnerId));

        assertThatThrownBy(() -> service.createTask(
                actorUserId, projectId, TaskType.DEV, "başlık", null, runnerId, null, null))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void gecerli_istek_task_olusturur_outbox_yazar_ve_olay_yayinlar() {
        projectExists();

        Task result = service.createTask(
                actorUserId, projectId, TaskType.DEV, "başlık", null, null, null, null);

        assertThat(result.getStatus()).isEqualTo(TaskStatus.QUEUED);
        verify(taskCreatedEventOutboxRepository).save(any());
        verify(eventPublisher, times(2)).publishEvent(any(Object.class));
    }

    @Test
    void FAILED_olmayan_task_yeniden_denenemez() {
        Task task = Task.create(organizationId, actorUserId, projectId, TaskType.DEV, "başlık", null);
        when(taskRepository.findById(any())).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.retryTask(actorUserId, UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Sadece FAILED");
    }

    @Test
    void max_retry_asilmissa_yeniden_denenemez() {
        Task task = Task.create(organizationId, actorUserId, projectId, TaskType.DEV, "başlık", null);
        task.transitionTo(TaskStatus.DISPATCHED);
        task.transitionTo(TaskStatus.FAILED);
        for (int i = 0; i < 3; i++) {
            task.incrementRetryCount();
        }
        when(taskRepository.findById(any())).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.retryTask(actorUserId, UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Maksimum yeniden deneme");
    }

    /**
     * completeTask() — AgentBridgeHandler.handleTaskResult() (runner/bridge) ile
     * TaskController.completeTask() (web) arasında paylaşılan TEK kod yolu. Bu testler her iki
     * çağıranın da beklediği davranışı (DISPATCHED→RUNNING ön geçişi, geçersiz status reddi)
     * doğruluyor.
     */
    @Test
    void completeTask_DISPATCHED_durumundayken_once_RUNNING_a_gecer_sonra_COMPLETED_eder() {
        UUID taskId = UUID.randomUUID();
        when(taskStateService.getCurrentStatus(taskId)).thenReturn(TaskStatus.DISPATCHED);

        service.completeTask(actorUserId, taskId, TaskStatus.COMPLETED, "bitti", "CLAUDE_CODE");

        verify(taskStateService).recordUsedAgent(taskId, "CLAUDE_CODE");
        verify(taskStateService).transition(taskId, TaskStatus.RUNNING, "İşe başlandı", actorUserId);
        verify(taskStateService).transition(taskId, TaskStatus.COMPLETED, "bitti", actorUserId);
    }

    @Test
    void completeTask_RUNNING_durumundayken_ara_gecise_gerek_duymadan_dogrudan_FAILED_eder() {
        UUID taskId = UUID.randomUUID();
        when(taskStateService.getCurrentStatus(taskId)).thenReturn(TaskStatus.RUNNING);

        service.completeTask(actorUserId, taskId, TaskStatus.FAILED, "olmadı", null);

        verify(taskStateService, never()).transition(eq(taskId), eq(TaskStatus.RUNNING), any(), any());
        verify(taskStateService).transition(taskId, TaskStatus.FAILED, "olmadı", actorUserId);
    }

    @Test
    void completeTask_bos_mesaj_icin_varsayilan_metin_kullanir() {
        UUID taskId = UUID.randomUUID();
        when(taskStateService.getCurrentStatus(taskId)).thenReturn(TaskStatus.RUNNING);

        service.completeTask(actorUserId, taskId, TaskStatus.COMPLETED, "  ", null);

        verify(taskStateService).transition(taskId, TaskStatus.COMPLETED, "Sonuç: COMPLETED", actorUserId);
    }

    @Test
    void completeTask_COMPLETED_FAILED_disinda_bir_status_ile_cagrilirsa_reddedilir() {
        UUID taskId = UUID.randomUUID();

        assertThatThrownBy(() -> service.completeTask(actorUserId, taskId, TaskStatus.QUEUED, "x", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("COMPLETED veya FAILED");

        verify(taskStateService, never()).transition(any(), any(), any(), any());
    }

    /**
     * AWAITING_APPROVAL'dan doğrudan COMPLETED/FAILED'e bir geçiş TaskStateMachine'de tanımlı
     * değil — bu, web'den tamamlama akışının onay kapısını bypass edemeyeceğinin garantisi.
     * Burada taskStateService mock'landığı için gerçek state machine'i tetiklemiyor; asıl
     * garanti TaskStateMachineTest'te doğrulanıyor, burada yalnızca completeTask'ın state
     * machine'in fırlattığı hatayı yutmadan ilettiğini kontrol ediyoruz.
     */
    @Test
    void createTask_prompt_verilirse_gorevde_saklanir() {
        projectExists();

        Task result = service.createTask(
                actorUserId, projectId, TaskType.DEV, "başlık", null, null, null, null, "  düzenli prompt metni  ");

        assertThat(result.getPrompt()).isEqualTo("  düzenli prompt metni  ");
    }

    @Test
    void createTask_prompt_bos_verilirse_null_kalir() {
        projectExists();

        Task result = service.createTask(
                actorUserId, projectId, TaskType.DEV, "başlık", null, null, null, null, "   ");

        assertThat(result.getPrompt()).isNull();
    }

    @Test
    void updatePrompt_gorevin_prompt_alanini_gunceller() {
        UUID taskId = UUID.randomUUID();
        Task task = Task.create(organizationId, actorUserId, projectId, TaskType.DEV, "başlık", null);
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));

        Task result = service.updatePrompt(actorUserId, taskId, "yeni prompt");

        assertThat(result.getPrompt()).isEqualTo("yeni prompt");
        verify(eventPublisher).publishEvent(any(Object.class));
    }

    @Test
    void completeTask_taskStateService_gecersiz_gecis_hatasi_firlatirsa_yutmaz() {
        UUID taskId = UUID.randomUUID();
        when(taskStateService.getCurrentStatus(taskId)).thenReturn(TaskStatus.AWAITING_APPROVAL);
        doThrow(new IllegalStateException("Geçersiz task durum geçişi: AWAITING_APPROVAL -> COMPLETED"))
                .when(taskStateService).transition(taskId, TaskStatus.COMPLETED, "bitti", actorUserId);

        assertThatThrownBy(() -> service.completeTask(actorUserId, taskId, TaskStatus.COMPLETED, "bitti", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Geçersiz task durum geçişi");
    }
}
