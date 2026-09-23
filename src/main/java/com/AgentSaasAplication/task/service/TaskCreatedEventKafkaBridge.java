package com.AgentSaasAplication.task.service;

import com.AgentSaasAplication.common.tenant.TenantContext;
import com.AgentSaasAplication.task.event.TaskCreatedEvent;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * `TaskCreatedEvent`'i Kafka'ya (`task.created` topic'i) yayınlamak için AFTER_COMMIT
 * fazında çalışan hızlı yol tetikleyicisi. `TaskOrchestrationService.createTask()`'ın DB
 * transaction'ı commit olmadan Kafka'ya hiçbir şey gönderilmez; asıl dispatch mantığı
 * `TaskDispatchListener.onTaskCreatedFromKafka()`'da, bir `@KafkaListener` tüketicisi
 * olarak yaşar.
 *
 * Yayının kendisi ve dayanıklılığı {@link TaskCreatedEventOutboxService}'e devredilmiştir:
 * `createTask()`/`retryTask()`, Task ile AYNI transaction'da bir
 * `task_created_event_outbox` satırı yazar. Bu satır veritabanında kalıcı olduğu için
 * buradaki yayın denemesi başarısız olsa bile `TaskCreatedEventOutboxScheduler`, broker
 * tekrar erişilebilir olana kadar periyodik olarak yeniden dener.
 *
 * DİKKAT — {@code @Async} zorunlu: AFTER_COMMIT (non-async) callback
 * `TransactionSynchronization.afterCompletion()` içinde, orijinal transaction'ın kapanışı
 * sürerken AYNI thread'de senkron çalışır. Bu pencerede yeni bir `@Transactional`
 * (REQUIRED) metodun temiz bir transaction açması güvenilir değildir ve çağrı
 * `TransactionRequiredException` ile başarısız olur. `@Async` ile çağrı tamamen ayrı bir
 * thread'e taşınır; orada `TransactionSynchronizationManager` durumu temiz olduğu için
 * `outboxService.publishForTask()` normal bir external bean çağrısı olarak yeni bir
 * transaction açabilir (AuditEventListener ile aynı desen).
 *
 * `@Async` olduğu için TenantContext ThreadLocal'ı yeni thread'e taşınmaz, elle set edilir
 * (yine AuditEventListener.onAuditEvent ile aynı desen).
 */
@Component
public class TaskCreatedEventKafkaBridge {

    public static final String TOPIC = "task.created";

    private final TaskCreatedEventOutboxService outboxService;

    public TaskCreatedEventKafkaBridge(TaskCreatedEventOutboxService outboxService) {
        this.outboxService = outboxService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTaskCreated(TaskCreatedEvent event) {
        TenantContext.set(event.organizationId());
        try {
            outboxService.publishForTask(event.taskId());
        } finally {
            TenantContext.clear();
        }
    }
}
