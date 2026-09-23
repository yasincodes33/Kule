package com.AgentSaasAplication.gateway;

import com.AgentSaasAplication.common.bridge.BridgeMessage;
import com.AgentSaasAplication.common.tenant.TenantContext;
import com.AgentSaasAplication.runner.service.RunnerConnectionService;
import com.AgentSaasAplication.task.domain.TaskStatus;
import com.AgentSaasAplication.task.service.TaskOrchestrationService;
import com.AgentSaasAplication.task.service.TaskStateService;
import tools.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Yerel runner'ların WebSocket kanalı. V15'ten itibaren karşı taraf bir "agent" değil,
 * bir kullanıcının makinesindeki runner — oturum kimliği runner_connections'a işaret ediyor.
 */
@Slf4j
@Component
public class AgentBridgeHandler extends TextWebSocketHandler {

    // Faz B: bir terminal oturumunun çıktısı, oturum kapanana kadar burada (bu instance'ta —
    // runner'ın bridge bağlantısı yaşadığı sürece hep AYNI instance'a düşer, bkz.
    // RunnerTerminalSessionRegistry Javadoc'u) biriktirilir; TERMINAL_CLOSED'da TEK bir log
    // kaydı olarak (varsa) bağlı olduğu görevin log akışına yazılır. Bellek taşmasını önlemek
    // için ~200KB'da kırpılır.
    private static final int MAX_TRANSCRIPT_CHARS = 200_000;
    private static final Pattern ANSI_PATTERN = Pattern.compile("\\[[0-9;]*[A-Za-z]");
    private final Map<UUID, StringBuilder> terminalTranscripts = new ConcurrentHashMap<>();

    private final BridgeSessionRegistry sessionRegistry;
    private final RunnerConnectionService runnerConnectionService;
    private final TaskStateService taskStateService;
    private final TaskOrchestrationService taskOrchestrationService;
    private final PendingToolCallRegistry pendingToolCallRegistry;
    private final RunnerTerminalSessionRegistry terminalSessionRegistry;
    private final ObjectMapper objectMapper;
    private final BridgeAiAssistHandler aiAssistHandler;
    private final com.AgentSaasAplication.task.service.TaskSummaryAssistant taskSummaryAssistant;

    public AgentBridgeHandler(BridgeSessionRegistry sessionRegistry,
                               RunnerConnectionService runnerConnectionService,
                               TaskStateService taskStateService,
                               TaskOrchestrationService taskOrchestrationService,
                               PendingToolCallRegistry pendingToolCallRegistry,
                               RunnerTerminalSessionRegistry terminalSessionRegistry,
                               ObjectMapper objectMapper,
                               BridgeAiAssistHandler aiAssistHandler,
                               com.AgentSaasAplication.task.service.TaskSummaryAssistant taskSummaryAssistant) {
        this.sessionRegistry = sessionRegistry;
        this.runnerConnectionService = runnerConnectionService;
        this.taskStateService = taskStateService;
        this.taskOrchestrationService = taskOrchestrationService;
        this.pendingToolCallRegistry = pendingToolCallRegistry;
        this.terminalSessionRegistry = terminalSessionRegistry;
        this.objectMapper = objectMapper;
        this.aiAssistHandler = aiAssistHandler;
        this.taskSummaryAssistant = taskSummaryAssistant;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        UUID runnerId = runnerId(session);
        UUID organizationId = organizationId(session);

        sessionRegistry.register(runnerId, session);
        withTenantContext(organizationId, () -> runnerConnectionService.recordHeartbeat(runnerId));

        log.info("Runner bridge bağlantısı kuruldu: runnerId={}, organizationId={}", runnerId, organizationId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        UUID runnerId = runnerId(session);
        UUID organizationId = organizationId(session);

        withTenantContext(organizationId, () -> handleMessage(runnerId, message.getPayload()));
    }

    private void handleMessage(UUID runnerId, String payload) {
        try {
            BridgeMessage bridgeMessage = objectMapper.readValue(payload, BridgeMessage.class);

            switch (bridgeMessage.type()) {
                case HEARTBEAT -> {
                    runnerConnectionService.recordHeartbeat(runnerId);
                    // Yönlendirme tablosunun TTL'i (120sn) heartbeat'le tazelenmeli,
                    // aksi halde runner hâlâ bağlıyken bile TTL dolup başka instance'lardan
                    // yanlışlıkla "bağlı değil" görünür.
                    sessionRegistry.refreshRouting(runnerId);
                }
                case TASK_RESULT -> handleTaskResult(bridgeMessage);
                case TASK_PROMPT_UPDATE -> handlePromptUpdate(bridgeMessage);
                case AI_ASSIST_REQUEST -> aiAssistHandler.handle(TenantContext.get(), runnerId,
                        bridgeMessage.callId(), bridgeMessage.taskId(), bridgeMessage.toolName(),
                        bridgeMessage.message());
                case LOG -> handleLog(bridgeMessage);
                case TOOL_RESULT -> pendingToolCallRegistry.complete(bridgeMessage.callId(), bridgeMessage);
                case TERMINAL_OUTPUT -> {
                    accumulateTranscript(bridgeMessage.terminalSessionId(), bridgeMessage.data());
                    terminalSessionRegistry.deliverToBrowser(bridgeMessage.terminalSessionId(), "output", bridgeMessage.data());
                }
                case TERMINAL_CLOSED -> {
                    flushTranscriptToTaskLog(bridgeMessage.terminalSessionId());
                    terminalSessionRegistry.deliverToBrowser(bridgeMessage.terminalSessionId(), "closed", null);
                }
                case TASK_DISPATCH, TOOL_CALL, TERMINAL_OPEN, TERMINAL_INPUT, TERMINAL_RESIZE, TERMINAL_CLOSE,
                        AI_ASSIST_RESULT ->
                        log.warn("Runner'dan beklenmeyen {} mesajı: runnerId={}", bridgeMessage.type(), runnerId);
            }
        } catch (Exception e) {
            log.warn("Bridge mesajı işlenemedi, bağlantı korunuyor: runnerId={}, hata={}", runnerId, e.getMessage());
            runnerConnectionService.recordHeartbeat(runnerId);
        }
    }

    /**
     * Runner'ın kendi terminalinden bildirdiği sonuç — TaskOrchestrationService.completeTask()'a
     * devrediliyor, bu da web'den (TaskController.completeTask) gelen aynı işlemle TEK kod
     * yolunu paylaşıyor (DISPATCHED→RUNNING ön geçişi dahil, artık burada kopyalanmıyor).
     * actorUserId null — bridge'den gelen bir sonucun belirli bir web kullanıcısı yok.
     */
    private void handleTaskResult(BridgeMessage msg) {
        if (msg.taskId() == null || msg.status() == null) return;

        TaskStatus newStatus = "COMPLETED".equalsIgnoreCase(msg.status())
                ? TaskStatus.COMPLETED : TaskStatus.FAILED;

        taskOrchestrationService.completeTask(null, msg.taskId(), newStatus,
                msg.message() != null ? msg.message() : ("Runner sonucu: " + newStatus), msg.usedAgent());

        // Masaüstünün akıttığı ham TERMINAL logları okunabilir bir özete indiriliyor.
        // Async — bu thread bridge'in mesaj işleyicisi, model çağrısını beklemesi doğru olmaz.
        taskSummaryAssistant.summarizeTerminalLogs(TenantContext.get(), msg.taskId(), msg.message());
    }

    /**
     * Masaüstü uygulamasının native görev panelinde prompt düzenlenip kaydedildiğinde — bkz.
     * TaskOrchestrationService.updatePrompt. actorUserId null (bridge'in kullanıcı-JWT'si yok,
     * completeTask'teki AYNI gerekçe) — updatePrompt bunu yalnızca audit event'e yazıyor, sorunsuz.
     */
    private void handlePromptUpdate(BridgeMessage msg) {
        if (msg.taskId() == null) return;
        taskOrchestrationService.updatePrompt(null, msg.taskId(), msg.message());
    }

    /**
     * taskStateService.appendLog() üzerinden yazılıyor (doğrudan taskLogRepository.save()
     * DEĞİL): bu metot private ve aynı sınıf içinden çağrıldığı için üzerine
     * @Transactional eklemek self-invocation nedeniyle proxy'yi devreye sokmaz,
     * TenantConnectionAspect tetiklenmez ve INSERT, RLS politikasını ihlal eder.
     */
    private void handleLog(BridgeMessage msg) {
        if (msg.taskId() == null) return;
        taskStateService.appendLog(msg.taskId(), msg.level(), msg.message());
    }

    private void accumulateTranscript(UUID terminalSessionId, String data) {
        if (data == null || data.isEmpty()) return;
        StringBuilder buffer = terminalTranscripts.computeIfAbsent(terminalSessionId, id -> new StringBuilder());
        synchronized (buffer) {
            if (buffer.length() < MAX_TRANSCRIPT_CHARS) {
                buffer.append(data);
                if (buffer.length() > MAX_TRANSCRIPT_CHARS) {
                    buffer.setLength(MAX_TRANSCRIPT_CHARS);
                    buffer.append("\n...(kırpıldı)");
                }
            }
        }
    }

    /**
     * Terminal oturumu kapanınca (TERMINAL_CLOSED) biriken çıktıyı, EĞER bu oturum bir göreve
     * bağlıysa (bkz. RunnerTerminalSessionRegistry.linkTask — yalnızca görev sayfasından "bu
     * araçla başlat" ile açılan oturumlarda dolu), TEK bir MODEL seviyeli log kaydı olarak
     * görevin kalıcı log akışına yazar. Bağlı değilse (RunnersPage'deki genel amaçlı terminal
     * erişimi) sessizce atlanır — veri kaybı değil, bilinçli bir tasarım kararı.
     */
    private void flushTranscriptToTaskLog(UUID terminalSessionId) {
        StringBuilder buffer = terminalTranscripts.remove(terminalSessionId);
        terminalSessionRegistry.getLinkedTaskId(terminalSessionId).ifPresent(taskId -> {
            if (buffer != null && !buffer.isEmpty()) {
                taskStateService.appendLog(taskId, "MODEL", stripAnsi(buffer.toString()));
            }
        });
        terminalSessionRegistry.unlinkTask(terminalSessionId);
    }

    private static String stripAnsi(String s) {
        return ANSI_PATTERN.matcher(s).replaceAll("");
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        UUID runnerId = runnerId(session);
        UUID organizationId = organizationId(session);

        sessionRegistry.remove(runnerId);
        withTenantContext(organizationId, () -> runnerConnectionService.markOffline(runnerId));

        log.info("Runner bridge bağlantısı kapandı: runnerId={}, organizationId={}, status={}",
                runnerId, organizationId, status);
    }

    private UUID runnerId(WebSocketSession session) {
        return (UUID) session.getAttributes().get("runnerConnectionId");
    }

    private UUID organizationId(WebSocketSession session) {
        return (UUID) session.getAttributes().get("organizationId");
    }

    private void withTenantContext(UUID organizationId, Runnable action) {
        TenantContext.set(organizationId);
        try {
            action.run();
        } finally {
            TenantContext.clear();
        }
    }
}
