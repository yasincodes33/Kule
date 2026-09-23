package com.AgentSaasAplication.task.service;

import com.AgentSaasAplication.task.domain.TaskCreatedEventOutbox;
import com.AgentSaasAplication.task.repository.TaskCreatedEventOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Bu servis, Kafka'ya yayının en-az-bir-kez teslim garantisinin gerçek kaynağı — canlı
 * testte (Kafka kasıtlı kapalıyken) davranışı zaten kanıtlandı, bu testler o davranışı hızlı ve
 * tekrarlanabilir bir regresyon koruması olarak sabitliyor: yayın başarısız olursa satır
 * published_at=NULL kalmalı (asla "yayınlandı" sanılıp kaybedilmemeli).
 */
@ExtendWith(MockitoExtension.class)
class TaskCreatedEventOutboxServiceTest {

    @Mock private TaskCreatedEventOutboxRepository outboxRepository;
    @Mock private KafkaTemplate<String, String> kafkaTemplate;

    private TaskCreatedEventOutboxService service;

    @BeforeEach
    void setUp() {
        service = new TaskCreatedEventOutboxService(outboxRepository, kafkaTemplate, new ObjectMapper());
    }

    private TaskCreatedEventOutbox freshRow() {
        return TaskCreatedEventOutbox.create(UUID.randomUUID(), UUID.randomUUID(), null, null, null);
    }

    @Test
    @SuppressWarnings("unchecked")
    void basarili_yayin_satiri_published_isaretler() {
        TaskCreatedEventOutbox row = freshRow();
        when(outboxRepository.findById(row.getId())).thenReturn(Optional.of(row));
        CompletableFuture<SendResult<String, String>> success =
                CompletableFuture.completedFuture(mock(SendResult.class));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(success);

        service.attemptPublish(row.getId());

        assertThat(row.isPublished()).isTrue();
        assertThat(row.getAttemptCount()).isEqualTo(1);
    }

    @Test
    void basarisiz_yayin_satiri_yayinlanmamis_birakir_ve_deneme_sayisini_artirir() {
        TaskCreatedEventOutbox row = freshRow();
        when(outboxRepository.findById(row.getId())).thenReturn(Optional.of(row));
        CompletableFuture<SendResult<String, String>> failing = new CompletableFuture<>();
        failing.completeExceptionally(new org.apache.kafka.common.errors.TimeoutException("broker yok"));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(failing);

        service.attemptPublish(row.getId());

        assertThat(row.isPublished()).isFalse();
        assertThat(row.getAttemptCount()).isEqualTo(1);
    }

    @Test
    void zaten_yayinlanmis_satir_icin_tekrar_kafka_ya_gonderilmez() {
        TaskCreatedEventOutbox row = freshRow();
        row.markPublished();
        when(outboxRepository.findById(row.getId())).thenReturn(Optional.of(row));

        service.attemptPublish(row.getId());

        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void var_olmayan_satir_icin_sessizce_hicbir_sey_yapmaz() {
        UUID missingId = UUID.randomUUID();
        when(outboxRepository.findById(missingId)).thenReturn(Optional.empty());

        service.attemptPublish(missingId);

        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void publishForTask_taskid_ile_en_yeni_yayinlanmamis_satiri_bulup_yayinlar() {
        UUID taskId = UUID.randomUUID();
        TaskCreatedEventOutbox row = freshRow();
        when(outboxRepository.findFirstByTaskIdAndPublishedAtIsNullOrderByCreatedAtDesc(taskId))
                .thenReturn(Optional.of(row));
        CompletableFuture<SendResult<String, String>> failing = new CompletableFuture<>();
        failing.completeExceptionally(new RuntimeException("beklenmeyen"));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(failing);

        service.publishForTask(taskId);

        assertThat(row.isPublished()).isFalse();
    }

    @Test
    void publishForTask_satir_bulunamazsa_hicbir_yayin_denemesi_yapmaz() {
        UUID taskId = UUID.randomUUID();
        when(outboxRepository.findFirstByTaskIdAndPublishedAtIsNullOrderByCreatedAtDesc(taskId))
                .thenReturn(Optional.empty());

        service.publishForTask(taskId);

        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void findUnpublishedIds_repository_sonucunu_id_listesine_cevirir() {
        TaskCreatedEventOutbox row1 = freshRow();
        TaskCreatedEventOutbox row2 = freshRow();
        when(outboxRepository.findByPublishedAtIsNullOrderByCreatedAtAsc()).thenReturn(List.of(row1, row2));

        assertThat(service.findUnpublishedIds()).containsExactly(row1.getId(), row2.getId());
    }
}
