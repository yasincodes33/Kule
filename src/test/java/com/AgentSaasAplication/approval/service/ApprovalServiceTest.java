package com.AgentSaasAplication.approval.service;

import com.AgentSaasAplication.approval.domain.ApprovalRequest;
import com.AgentSaasAplication.approval.repository.ApprovalRequestRepository;
import com.AgentSaasAplication.common.exceptions.NotFoundException;
import com.AgentSaasAplication.common.tenant.TenantContext;
import com.AgentSaasAplication.identity.service.MembershipAuthorizationService;
import com.AgentSaasAplication.task.domain.TaskStatus;
import com.AgentSaasAplication.task.service.TaskStateService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ApprovalService, Faz 10'un onay akışının tek giriş noktası — burada bulunacak bir
 * hata (ör. zaten reddedilmiş bir isteğin tekrar onaylanabilmesi) doğrudan güvenlik açığı olur.
 */
@ExtendWith(MockitoExtension.class)
class ApprovalServiceTest {

    @Mock
    private ApprovalRequestRepository approvalRequestRepository;
    @Mock
    private TaskStateService taskStateService;
    @Mock
    private MembershipAuthorizationService membershipAuthorizationService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ApprovalService approvalService;

    private final UUID organizationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        approvalService = new ApprovalService(
                approvalRequestRepository, taskStateService, membershipAuthorizationService, eventPublisher);
        TenantContext.set(organizationId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void bekleyen_istek_yoksa_onay_istegi_olusturulur_ve_task_awaiting_approval_a_gecer() {
        UUID taskId = UUID.randomUUID();
        UUID requestedBy = UUID.randomUUID();
        when(approvalRequestRepository.existsByTaskIdAndStatus(eq(taskId), any())).thenReturn(false);
        when(approvalRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ApprovalRequest result = approvalService.requestApproval(taskId, requestedBy);

        assertThat(result.getTaskId()).isEqualTo(taskId);
        verify(taskStateService).transition(eq(taskId), eq(TaskStatus.AWAITING_APPROVAL), any(), eq(requestedBy));
        verify(eventPublisher).publishEvent(any(com.AgentSaasAplication.common.audit.AuditEvent.class));
    }

    @Test
    void zaten_bekleyen_bir_istek_varsa_yeni_istek_reddedilir() {
        UUID taskId = UUID.randomUUID();
        when(approvalRequestRepository.existsByTaskIdAndStatus(eq(taskId), any())).thenReturn(true);

        assertThatThrownBy(() -> approvalService.requestApproval(taskId, UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("zaten bekleyen");

        verify(taskStateService, never()).transition(any(), any(), any(), any());
        verify(approvalRequestRepository, never()).save(any());
    }

    @Test
    void onaylama_yetki_kontrolunden_gecmeyen_kullaniciyi_reddeder() {
        UUID approvalId = UUID.randomUUID();
        UUID approverId = UUID.randomUUID();
        org.springframework.security.access.AccessDeniedException denied =
                new org.springframework.security.access.AccessDeniedException("yetkisiz");
        org.mockito.Mockito.doThrow(denied)
                .when(membershipAuthorizationService).requireApprovalPermission(organizationId, approverId);

        assertThatThrownBy(() -> approvalService.approve(approvalId, approverId)).isSameAs(denied);

        verify(approvalRequestRepository, never()).findById(any());
    }

    @Test
    void onaylama_basariliysa_task_running_a_geciyor_ve_denetim_olayi_yayinlaniyor() {
        UUID taskId = UUID.randomUUID();
        UUID approverId = UUID.randomUUID();
        ApprovalRequest request = ApprovalRequest.requestFor(
                organizationId, taskId, UUID.randomUUID(), Instant.now().plusSeconds(3600));
        when(approvalRequestRepository.findById(any())).thenReturn(Optional.of(request));

        ApprovalRequest result = approvalService.approve(UUID.randomUUID(), approverId);

        assertThat(result.getStatus().name()).isEqualTo("APPROVED");
        verify(taskStateService).transition(eq(taskId), eq(TaskStatus.RUNNING), any(), eq(approverId));
        verify(eventPublisher).publishEvent(any(com.AgentSaasAplication.common.audit.AuditEvent.class));
    }

    @Test
    void zaten_karara_baglanmis_bir_istegi_tekrar_onaylamak_domain_kuraliyla_reddedilir() {
        UUID approverId = UUID.randomUUID();
        ApprovalRequest request = ApprovalRequest.requestFor(
                organizationId, UUID.randomUUID(), UUID.randomUUID(), Instant.now().plusSeconds(3600));
        request.approve(UUID.randomUUID());
        when(approvalRequestRepository.findById(any())).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> approvalService.approve(UUID.randomUUID(), approverId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("zaten karara bağlanmış");

        verify(taskStateService, never()).transition(any(), any(), any(), any());
    }

    @Test
    void reddetme_sebep_verilmisse_task_log_mesajina_ve_denetim_metadata_sina_ekleniyor() {
        UUID taskId = UUID.randomUUID();
        UUID approverId = UUID.randomUUID();
        ApprovalRequest request = ApprovalRequest.requestFor(
                organizationId, taskId, UUID.randomUUID(), Instant.now().plusSeconds(3600));
        when(approvalRequestRepository.findById(any())).thenReturn(Optional.of(request));

        approvalService.reject(UUID.randomUUID(), approverId, "riskli komut");

        verify(taskStateService).transition(
                eq(taskId), eq(TaskStatus.REJECTED),
                org.mockito.ArgumentMatchers.contains("riskli komut"), eq(approverId));
    }

    @Test
    void reddetme_sebep_verilmemisse_genel_bir_mesajla_reddediliyor() {
        UUID taskId = UUID.randomUUID();
        ApprovalRequest request = ApprovalRequest.requestFor(
                organizationId, taskId, UUID.randomUUID(), Instant.now().plusSeconds(3600));
        when(approvalRequestRepository.findById(any())).thenReturn(Optional.of(request));

        approvalService.reject(UUID.randomUUID(), UUID.randomUUID(), null);

        verify(taskStateService).transition(
                eq(taskId), eq(TaskStatus.REJECTED), eq("Onay reddedildi"), any());
    }

    @Test
    void var_olmayan_onay_istegi_notfound_firlatir() {
        when(approvalRequestRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> approvalService.getApproval(UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class);
    }
}
