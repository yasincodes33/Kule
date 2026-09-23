package com.AgentSaasAplication.task.service;

import com.AgentSaasAplication.agent.service.AiAssistService;
import com.AgentSaasAplication.common.domain.ModelTier;
import com.AgentSaasAplication.common.tenant.TenantContext;
import com.AgentSaasAplication.task.domain.TaskLog;
import com.AgentSaasAplication.task.repository.TaskLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Terminal logu özeti — Görev kapanırken, masaüstünün akıttığı ham `TERMINAL` loglarını
 * (bkz. Faz G) okunabilir tek bir "ne yapıldı" özetine indirir ve göreve `SUMMARY` seviyeli bir
 * log olarak yazar.
 *
 * Neden ayrı bir bean ve {@code @Async}: çağrı, bridge'in WebSocket mesaj işleyicisinden geliyor
 * (TASK_RESULT). Model çağrısı saniyeler sürebilir; o thread'i tutmak aynı runner'ın
 * heartbeat'lerini geciktirirdi. {@code @Async} thread'i TenantContext'i DEVRALMADIĞI için
 * (TaskCreatedEventKafkaBridge'de kanıtlanmış desen) organizasyon kimliği elle taşınıyor.
 */
@Slf4j
@Component
public class TaskSummaryAssistant {

    /** Bu sayıdan az terminal satırı varsa özetlemeye değmez — zaten okunabilir. */
    private static final int MIN_CHARS_TO_SUMMARIZE = 400;

    private final TaskLogRepository taskLogRepository;
    private final TaskStateService taskStateService;
    private final AiAssistService aiAssistService;

    public TaskSummaryAssistant(TaskLogRepository taskLogRepository, TaskStateService taskStateService,
                                AiAssistService aiAssistService) {
        this.taskLogRepository = taskLogRepository;
        this.taskStateService = taskStateService;
        this.aiAssistService = aiAssistService;
    }

    @Async
    public void summarizeTerminalLogs(UUID organizationId, UUID taskId, String resultMessage) {
        TenantContext.set(organizationId);
        try {
            String transcript = readTerminalTranscript(taskId);
            if (transcript.length() < MIN_CHARS_TO_SUMMARIZE) {
                return;
            }
            String system = """
                    Sana bir yazılım görevinin terminal kaydı veriliyor. Kısa bir özet yaz: \
                    hangi komutlar çalıştırıldı, ne değişti, hata çıktı mı. En fazla 8 madde, \
                    her madde tek satır. Giriş/kapanış cümlesi yazma. Yanıtı Türkçe ver.""";
            String user = (resultMessage != null && !resultMessage.isBlank()
                    ? "Kullanıcının sonuç açıklaması: " + resultMessage + "\n\n" : "")
                    + "Terminal kaydı:\n" + transcript;

            Optional<String> summary = aiAssistService.ask(ModelTier.BUDGET, system, user);
            summary.ifPresent(text -> taskStateService.appendLog(taskId, "SUMMARY", text));
        } catch (Exception e) {
            // Yardımcı özellik — görev zaten kapandı, özet çıkmaması akışı etkilemez.
            log.warn("Terminal logu özetlenemedi: taskId={}, hata={}", taskId, e.toString());
        } finally {
            TenantContext.clear();
        }
    }

    @Transactional(readOnly = true)
    public String readTerminalTranscript(UUID taskId) {
        List<TaskLog> logs = taskLogRepository.findByTaskIdOrderByTimestampAsc(taskId);
        return logs.stream()
                .filter(l -> "TERMINAL".equalsIgnoreCase(l.getLevel()))
                .map(TaskLog::getMessage)
                .collect(Collectors.joining("\n"));
    }
}
