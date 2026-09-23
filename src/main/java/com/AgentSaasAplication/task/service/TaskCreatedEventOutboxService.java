package com.AgentSaasAplication.task.service;

import com.AgentSaasAplication.task.domain.TaskCreatedEventOutbox;
import com.AgentSaasAplication.task.event.TaskCreatedEvent;
import com.AgentSaasAplication.task.repository.TaskCreatedEventOutboxRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Outbox satırlarını gerçekten Kafka'ya yayınlayan tek yer — hem
 * {@link TaskCreatedEventKafkaBridge}'in AFTER_COMMIT hızlı yolu hem de
 * {@link TaskCreatedEventOutboxScheduler}'ın periyodik yeniden deneme taraması buraya
 * delege ediyor, böylece yayın mantığı iki kez yazılmıyor.
 *
 * DİKKAT: repository çağrıları asla doğrudan bir {@code @TransactionalEventListener} ya
 * da {@code @Scheduled} metodunun GÖVDESİNDEN yapılmaz. TenantConnectionAspect yalnızca
 * dışarıdan normal bir bean çağrısıyla invoke edilen {@code @Transactional} metotlarda
 * güvenilir şekilde tetiklenir; bu yüzden hem {@code publishForTask} hem
 * {@code attemptPublish} burada, ayrı bir bean'de {@code @Transactional} olarak tanımlı.
 * Çağrı yapanlar (Kafka bridge ve scheduler) bunlara her zaman dışarıdan normal bir
 * metot çağrısıyla ulaşır (StaleDispatchScheduler/StaleDispatchService ile aynı desen).
 *
 * Yayın {@code kafkaTemplate.send(...).get(timeout)} ile senkron bekleniyor: bu metot
 * zaten worker/scheduler thread'inde çalışıyor ve broker'ın ack'ini görmeden satırı
 * published_at ile işaretlemek "yayınlandı sanıp kaybetme" riskini geri getirirdi.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class TaskCreatedEventOutboxService {

    private static final long SEND_ACK_TIMEOUT_SECONDS = 5;

    private final TaskCreatedEventOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public TaskCreatedEventOutboxService(TaskCreatedEventOutboxRepository outboxRepository,
                                          KafkaTemplate<String, String> kafkaTemplate,
                                          ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public List<UUID> findUnpublishedIds() {
        return outboxRepository.findByPublishedAtIsNullOrderByCreatedAtAsc().stream()
                .map(TaskCreatedEventOutbox::getId)
                .toList();
    }

    /** {@link TaskCreatedEventKafkaBridge}'in AFTER_COMMIT hızlı yolu için — taskId'den satırı bulur. */
    @Transactional
    public void publishForTask(UUID taskId) {
        outboxRepository.findFirstByTaskIdAndPublishedAtIsNullOrderByCreatedAtDesc(taskId)
                .ifPresentOrElse(this::doPublish,
                        () -> log.error("TaskCreatedEvent için outbox satırı bulunamadı — "
                                        + "createTask()/retryTask() ile aynı transaction'da yazılmamış "
                                        + "olabilir: taskId={}", taskId));
    }

    /** {@link TaskCreatedEventOutboxScheduler}'ın periyodik yeniden deneme taraması için — id bilinen satır. */
    @Transactional
    public void attemptPublish(UUID outboxId) {
        outboxRepository.findById(outboxId)
                .filter(row -> !row.isPublished())
                .ifPresent(this::doPublish);
    }

    private void doPublish(TaskCreatedEventOutbox row) {
        TaskCreatedEvent event = new TaskCreatedEvent(row.getTaskId(), row.getTenantId(),
                row.getPreferredAgentConnectionId(), row.getPreferredRunnerConnectionId(),
                row.getPreferredModelTier());

        row.recordAttempt();
        try {
            String json = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(TaskCreatedEventKafkaBridge.TOPIC, row.getTaskId().toString(), json)
                    .get(SEND_ACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            row.markPublished();
            log.info("TaskCreatedEvent Kafka'ya yayınlandı: taskId={}, outboxId={}, deneme={}",
                    row.getTaskId(), row.getId(), row.getAttemptCount());
        } catch (Exception e) {
            log.error("TaskCreatedEvent Kafka'ya yayınlanamadı (deneme={}), outbox satırı kalıcı — "
                            + "daha sonra tekrar denenecek: taskId={}, outboxId={}",
                    row.getAttemptCount(), row.getTaskId(), row.getId(), e);
        }
    }
}
