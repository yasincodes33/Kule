package com.AgentSaasAplication.task.service;

import com.AgentSaasAplication.common.domain.UsedAgent;
import com.AgentSaasAplication.common.task.TaskLogPublisher;
import com.AgentSaasAplication.common.tenant.TenantContext;
import com.AgentSaasAplication.task.domain.Task;
import com.AgentSaasAplication.task.domain.TaskLog;
import com.AgentSaasAplication.task.domain.TaskStatus;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TaskStateService.transition(), state machine geçişlerinin TEK giriş noktası —
 * recordUsedAgent()'ın "tanınmayan araç adı gelirse görevi düşürme, sessizce yok say" kuralı da
 * burada, runner'ın gönderdiği serbest metin (usedAgent raporu) hiçbir zaman task'ı bozmamalı.
 */
@ExtendWith(MockitoExtension.class)
class TaskStateServiceTest {

    @Mock private TaskRepository taskRepository;
    @Mock private TaskLogRepository taskLogRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private TaskLogPublisher taskLogPublisher;

    private TaskStateService service;

    private final UUID organizationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new TaskStateService(taskRepository, taskLogRepository, eventPublisher, taskLogPublisher);
        TenantContext.set(organizationId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Task queuedTask() {
        return Task.create(organizationId, UUID.randomUUID(), UUID.randomUUID(),
                com.AgentSaasAplication.common.domain.TaskType.DEV, "başlık", null);
    }

    @Test
    void gecerli_gecis_durumu_gunceller_log_yazar_ve_yayinlar() {
        Task task = queuedTask();
        when(taskRepository.findById(any())).thenReturn(Optional.of(task));
        when(taskLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Task result = service.transition(UUID.randomUUID(), TaskStatus.DISPATCHED, "runner'a gönderildi", UUID.randomUUID());

        assertThat(result.getStatus()).isEqualTo(TaskStatus.DISPATCHED);
        verify(taskLogPublisher).publish(any(), any(), any(), any(), any(), org.mockito.ArgumentMatchers.eq("DISPATCHED"));
        verify(eventPublisher).publishEvent(any(com.AgentSaasAplication.common.audit.AuditEvent.class));
    }

    @Test
    void gecersiz_gecis_state_machine_tarafindan_reddedilir() {
        Task task = queuedTask(); // QUEUED
        when(taskRepository.findById(any())).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.transition(UUID.randomUUID(), TaskStatus.COMPLETED, null, null))
                .isInstanceOf(IllegalStateException.class);

        verify(taskLogRepository, never()).save(any());
    }

    @Test
    void log_mesaji_verilmezse_varsayilan_durum_gecis_mesaji_kullanilir() {
        Task task = queuedTask();
        when(taskRepository.findById(any())).thenReturn(Optional.of(task));
        when(taskLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.transition(UUID.randomUUID(), TaskStatus.DISPATCHED, null, null);

        org.mockito.ArgumentCaptor<TaskLog> captor = org.mockito.ArgumentCaptor.forClass(TaskLog.class);
        verify(taskLogRepository).save(captor.capture());
        assertThat(captor.getValue().getMessage()).contains("QUEUED").contains("DISPATCHED");
    }

    @Test
    void taninan_arac_adi_kucuk_harfle_gelse_bile_kaydedilir() {
        Task task = queuedTask();
        when(taskRepository.findById(any())).thenReturn(Optional.of(task));

        service.recordUsedAgent(UUID.randomUUID(), "claude_code");

        assertThat(task.getUsedAgent()).isEqualTo(UsedAgent.CLAUDE_CODE);
    }

    @Test
    void taninmayan_arac_adi_sessizce_yok_sayilir_exception_atmaz_ve_repository_ye_dokunmaz() {
        service.recordUsedAgent(UUID.randomUUID(), "gpt-ozel-bir-arac");

        verify(taskRepository, never()).findById(any());
    }

    @Test
    void bos_arac_adi_repository_ye_hic_dokunmadan_yok_sayilir() {
        service.recordUsedAgent(UUID.randomUUID(), "  ");
        service.recordUsedAgent(UUID.randomUUID(), null);

        verify(taskRepository, never()).findById(any());
    }

    @Test
    void var_olmayan_task_icin_gecis_notfound_firlatir() {
        when(taskRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.transition(UUID.randomUUID(), TaskStatus.DISPATCHED, null, null))
                .isInstanceOf(com.AgentSaasAplication.common.exceptions.NotFoundException.class);
    }
}
