package com.AgentSaasAplication.task.service;

import com.AgentSaasAplication.common.exceptions.NotFoundException;
import com.AgentSaasAplication.common.tenant.TenantContext;
import com.AgentSaasAplication.runner.domain.RunnerConnection;
import com.AgentSaasAplication.runner.service.RunnerConnectionService;
import com.AgentSaasAplication.task.domain.Task;
import com.AgentSaasAplication.task.domain.TaskStatus;
import com.AgentSaasAplication.task.repository.TaskRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Faz 12'de gerçek bir uçtan uca testte bulunan hatanın (runner koparsa task sonsuza
 * dek DISPATCHED'de asılı kalıyordu) kalıcı regresyon koruması. "Hâlâ online ama gerçekten uzun
 * süren görev yanlış pozitif olmasın" kuralı — runnerIsGone()'un yalnızca gerçekten OFFLINE
 * runner'lar için true dönmesi — burada test ediliyor.
 */
@ExtendWith(MockitoExtension.class)
class StaleDispatchServiceTest {

    @Mock private TaskRepository taskRepository;
    @Mock private TaskStateService taskStateService;
    @Mock private RunnerConnectionService runnerConnectionService;

    private StaleDispatchService service;

    private final UUID organizationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new StaleDispatchService(taskRepository, taskStateService, runnerConnectionService);
        TenantContext.set(organizationId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Task dispatchedTaskWithRunner(UUID runnerId) {
        Task task = Task.create(organizationId, UUID.randomUUID(), UUID.randomUUID(),
                com.AgentSaasAplication.common.domain.TaskType.DEV, "başlık", null);
        task.transitionTo(TaskStatus.DISPATCHED);
        task.assignRunner(runnerId);
        return task;
    }

    @Test
    void runner_i_atanmamis_task_asili_sayilmaz() {
        Task task = Task.create(organizationId, UUID.randomUUID(), UUID.randomUUID(),
                com.AgentSaasAplication.common.domain.TaskType.DEV, "başlık", null);
        task.transitionTo(TaskStatus.DISPATCHED); // runnerConnectionId hiç atanmadı
        when(taskRepository.findByStatusInAndUpdatedAtBefore(any(), any())).thenReturn(List.of(task));

        assertThat(service.findStaleDispatchIds(Instant.now())).isEmpty();
    }

    @Test
    void runner_hala_online_ise_task_asili_sayilmaz_yanlis_pozitif_uretmez() {
        UUID runnerId = UUID.randomUUID();
        Task task = dispatchedTaskWithRunner(runnerId);
        RunnerConnection onlineRunner = RunnerConnection.register(organizationId, UUID.randomUUID(), null, "ev");
        onlineRunner.recordHeartbeat();
        when(taskRepository.findByStatusInAndUpdatedAtBefore(any(), any())).thenReturn(List.of(task));
        when(runnerConnectionService.getRunnerConnection(runnerId)).thenReturn(onlineRunner);

        assertThat(service.findStaleDispatchIds(Instant.now())).isEmpty();
    }

    @Test
    void runner_offline_ise_task_asili_sayilir() {
        UUID runnerId = UUID.randomUUID();
        Task task = dispatchedTaskWithRunner(runnerId);
        UUID taskId = UUID.randomUUID();
        ReflectionTestUtils.setField(task, "id", taskId);
        RunnerConnection offlineRunner = RunnerConnection.register(organizationId, UUID.randomUUID(), null, "ev");
        when(taskRepository.findByStatusInAndUpdatedAtBefore(any(), any())).thenReturn(List.of(task));
        when(runnerConnectionService.getRunnerConnection(runnerId)).thenReturn(offlineRunner);

        assertThat(service.findStaleDispatchIds(Instant.now())).containsExactly(taskId);
    }

    @Test
    void runner_kaydi_tamamen_silinmisse_de_task_asili_sayilir() {
        UUID runnerId = UUID.randomUUID();
        Task task = dispatchedTaskWithRunner(runnerId);
        UUID taskId = UUID.randomUUID();
        ReflectionTestUtils.setField(task, "id", taskId);
        when(taskRepository.findByStatusInAndUpdatedAtBefore(any(), any())).thenReturn(List.of(task));
        when(runnerConnectionService.getRunnerConnection(runnerId))
                .thenThrow(new NotFoundException("Runner bağlantısı bulunamadı: " + runnerId));

        assertThat(service.findStaleDispatchIds(Instant.now())).containsExactly(taskId);
    }

    @Test
    void failStaleDispatch_task_i_failed_e_cekip_retry_icin_mesaj_birakir() {
        UUID taskId = UUID.randomUUID();

        service.failStaleDispatch(taskId);

        org.mockito.Mockito.verify(taskStateService).transition(
                org.mockito.ArgumentMatchers.eq(taskId),
                org.mockito.ArgumentMatchers.eq(TaskStatus.FAILED),
                org.mockito.ArgumentMatchers.contains("retry"),
                org.mockito.ArgumentMatchers.isNull());
    }
}
