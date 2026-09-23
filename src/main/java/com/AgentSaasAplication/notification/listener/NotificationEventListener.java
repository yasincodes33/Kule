package com.AgentSaasAplication.notification.listener;

import com.AgentSaasAplication.approval.domain.ApprovalRequest;
import com.AgentSaasAplication.approval.repository.ApprovalRequestRepository;
import com.AgentSaasAplication.common.audit.AuditEvent;
import com.AgentSaasAplication.common.tenant.RlsSessionVariables;
import com.AgentSaasAplication.common.tenant.TenantContext;
import com.AgentSaasAplication.identity.domain.Membership;
import com.AgentSaasAplication.identity.domain.MembershipStatus;
import com.AgentSaasAplication.identity.domain.Role;
import com.AgentSaasAplication.identity.domain.User;
import com.AgentSaasAplication.identity.repository.MembershipRepository;
import com.AgentSaasAplication.identity.repository.UserRepository;
import com.AgentSaasAplication.notification.domain.NotificationType;
import com.AgentSaasAplication.notification.service.NotificationService;
import com.AgentSaasAplication.runner.domain.RunnerTerminalSessionRequest;
import com.AgentSaasAplication.runner.repository.RunnerTerminalSessionRequestRepository;
import com.AgentSaasAplication.task.domain.Task;
import com.AgentSaasAplication.task.repository.TaskRepository;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
public class NotificationEventListener {

    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final MembershipRepository membershipRepository;
    private final ApprovalRequestRepository approvalRequestRepository;
    private final TaskRepository taskRepository;
    private final RunnerTerminalSessionRequestRepository runnerTerminalSessionRequestRepository;
    private final EntityManager entityManager;

    public NotificationEventListener(NotificationService notificationService,
                                      UserRepository userRepository,
                                      MembershipRepository membershipRepository,
                                      ApprovalRequestRepository approvalRequestRepository,
                                      TaskRepository taskRepository,
                                      RunnerTerminalSessionRequestRepository runnerTerminalSessionRequestRepository,
                                      EntityManager entityManager) {
        this.notificationService = notificationService;
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
        this.approvalRequestRepository = approvalRequestRepository;
        this.taskRepository = taskRepository;
        this.runnerTerminalSessionRequestRepository = runnerTerminalSessionRequestRepository;
        this.entityManager = entityManager;
    }

    /**
     * Gerçek bir uçtan uca testte (çoklu APPROVER fan-out denemesi) bulundu:
     * handleApprovalRequested()'in membershipRepository sorgusu, handleApprovalDecision()'ın
     * approvalRequestRepository.findById()'i ve handleTaskStatusChanged()'ın
     * taskRepository.findById()'i RLS tarafından SESSİZCE engelleniyordu — APPROVAL_REQUESTED/
     * APPROVED/REJECTED/EXPIRED ve TASK_COMPLETED/FAILED bildirimlerinin HİÇBİRİ asla
     * üretilmiyordu (boş sonuç geldiği için ilgili metotlar sessizce hiçbir şey yapmadan
     * dönüyordu, hiçbir hata/log yoktu).
     *
     * Önce TenantConnectionAspect'in @Transactional üzerinden otomatik tetiklenmesi denendi
     * (Faz 8'deki AgentBridgeHandler.handleLog düzeltmesiyle aynı desen, Propagation.
     * REQUIRES_NEW ile — AFTER_COMMIT'te orijinal transaction zaten kapandığı için düz
     * @Transactional Spring'in kendi doğrulamasına takılıyordu). DEBUG log'la doğrulandı:
     * TenantContext.get() doğru org'u gösteriyordu ama sorgu YİNE DE boş dönüyordu —
     * @Async + @TransactionalEventListener'ın kendi reflection tabanlı çağrı mekanizması
     * aspect'in @Before pointcut'ının yakalayabileceği proxy zincirinden geçmiyor gibi
     * görünüyor. Aspect'e güvenmek yerine RLS oturum değişkeni burada AÇIKÇA ayarlanıyor —
     * framework'ün iç detaylarına bağımlı kalmadan garantili çalışan tek yol bu.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAuditEvent(AuditEvent event) {
        TenantContext.set(event.organizationId());
        RlsSessionVariables.setTenantId(entityManager, event.organizationId());
        try {
            switch (event.action()) {
                case "MEMBER_INVITED" -> handleMemberInvited(event);
                case "APPROVAL_REQUESTED" -> handleApprovalRequested(event);
                case "APPROVAL_APPROVED" -> handleApprovalDecision(event, NotificationType.APPROVAL_APPROVED);
                case "APPROVAL_REJECTED" -> handleApprovalDecision(event, NotificationType.APPROVAL_REJECTED);
                case "TASK_STATUS_CHANGED" -> handleTaskStatusChanged(event);
                case "APPROVAL_EXPIRED" -> handleApprovalDecision(event, NotificationType.APPROVAL_EXPIRED);
                case "TERMINAL_SESSION_REQUESTED" -> handleTerminalSessionRequested(event);
                case "TERMINAL_SESSION_APPROVED" -> handleTerminalSessionDecision(event, NotificationType.TERMINAL_SESSION_APPROVED);
                case "TERMINAL_SESSION_REJECTED" -> handleTerminalSessionDecision(event, NotificationType.TERMINAL_SESSION_REJECTED);
                default -> { /* bu action için bildirim tanımlı değil */ }
            }
        } catch (Exception e) {
            log.error("Bildirim işlenemedi: action={}, entityId={}", event.action(), event.entityId(), e);
        } finally {
            TenantContext.clear();
        }
    }

    private void handleMemberInvited(AuditEvent event) {
        String invitedEmail = (String) event.metadata().get("invitedEmail");
        if (invitedEmail == null) return;

        Optional<User> invitedUser = userRepository.findByEmail(invitedEmail);
        if (invitedUser.isEmpty()) {
            log.info("Davet edilen kullanıcı henüz kayıtlı değil, bildirim ertelendi: email={}", invitedEmail);
            return;
        }

        notificationService.create(event.organizationId(), invitedUser.get().getId(),
                NotificationType.MEMBERSHIP_INVITED,
                Map.of("membershipId", event.entityId().toString(),
                       "role", String.valueOf(event.metadata().get("role"))));
    }

    private void handleApprovalRequested(AuditEvent event) {
        List<Membership> approvers = membershipRepository.findByTenantIdAndRoleInAndStatus(
                event.organizationId(), List.of(Role.APPROVER, Role.ADMIN, Role.OWNER), MembershipStatus.ACTIVE);

        for (Membership approver : approvers) {
            notificationService.create(event.organizationId(), approver.getUserId(),
                    NotificationType.APPROVAL_REQUESTED,
                    Map.of("approvalId", event.entityId().toString()));
        }
    }

    private void handleApprovalDecision(AuditEvent event, NotificationType type) {
        ApprovalRequest approvalRequest = approvalRequestRepository.findById(event.entityId()).orElse(null);
        if (approvalRequest == null) return;

        notificationService.create(event.organizationId(), approvalRequest.getRequestedBy(), type,
                Map.of("approvalId", event.entityId().toString(),
                       "taskId", approvalRequest.getTaskId().toString()));
    }

    private void handleTerminalSessionRequested(AuditEvent event) {
        List<Membership> approvers = membershipRepository.findByTenantIdAndRoleInAndStatus(
                event.organizationId(), List.of(Role.APPROVER, Role.ADMIN, Role.OWNER), MembershipStatus.ACTIVE);

        for (Membership approver : approvers) {
            notificationService.create(event.organizationId(), approver.getUserId(),
                    NotificationType.TERMINAL_SESSION_REQUESTED,
                    Map.of("requestId", event.entityId().toString(),
                           "runnerConnectionId", String.valueOf(event.metadata().get("runnerConnectionId"))));
        }
    }

    private void handleTerminalSessionDecision(AuditEvent event, NotificationType type) {
        RunnerTerminalSessionRequest request = runnerTerminalSessionRequestRepository.findById(event.entityId()).orElse(null);
        if (request == null) return;

        notificationService.create(event.organizationId(), request.getRequestedBy(), type,
                Map.of("requestId", event.entityId().toString(),
                       "runnerConnectionId", request.getRunnerConnectionId().toString()));
    }

    private void handleTaskStatusChanged(AuditEvent event) {
        String to = (String) event.metadata().get("to");
        if (!"COMPLETED".equals(to) && !"FAILED".equals(to)) return;

        Task task = taskRepository.findById(event.entityId()).orElse(null);
        if (task == null || task.getCreatedBy() == null) return;

        NotificationType type = "COMPLETED".equals(to) ? NotificationType.TASK_COMPLETED : NotificationType.TASK_FAILED;
        notificationService.create(event.organizationId(), task.getCreatedBy(), type,
                Map.of("taskId", task.getId().toString(), "title", task.getTitle()));
    }
}