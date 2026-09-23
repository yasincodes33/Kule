package com.AgentSaasAplication.task.repository;

import com.AgentSaasAplication.task.domain.TaskCreatedEventOutbox;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskCreatedEventOutboxRepository extends JpaRepository<TaskCreatedEventOutbox, UUID> {

    // TaskCreatedEventOutboxScheduler'ın periyodik yeniden deneme taraması için.
    List<TaskCreatedEventOutbox> findByPublishedAtIsNullOrderByCreatedAtAsc();

    // AFTER_COMMIT hızlı yol (TaskCreatedEventKafkaBridge), az önce aynı transaction'da
    // yazılan satırı taskId ile geri buluyor — retryTask() aynı taskId için ikinci bir satır
    // yazabildiğinden en yeni yayınlanmamış satır seçiliyor.
    Optional<TaskCreatedEventOutbox> findFirstByTaskIdAndPublishedAtIsNullOrderByCreatedAtDesc(UUID taskId);
}
