package com.AgentSaasAplication.common.bridge;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public record BridgeMessage(
        BridgeMessageType type,
        UUID taskId,
        String taskType,
        String title,
        String status,
        String level,
        String message,
        UUID callId,
        String toolName,
        Map<String, Object> arguments,
        Boolean success,
        /** Runner'ın TASK_RESULT ile bildirdiği, görevin hangi araçla çözüldüğü. Opsiyonel. */
        String usedAgent,
        /** TERMINAL_* mesajlarını bir terminal oturumuna bağlar. */
        UUID terminalSessionId,
        /** TERMINAL_INPUT/TERMINAL_OUTPUT taşıyıcısı — ham (ANSI dahil) shell verisi. */
        String data,
        /** TERMINAL_OPEN/TERMINAL_RESIZE — terminal genişliği. */
        Integer cols,
        /** TERMINAL_OPEN/TERMINAL_RESIZE — terminal yüksekliği. */
        Integer rows
) {
    /**
     * `prompt`, mevcut genel amaçlı `message` alanına taşınır — TASK_DISPATCH bunu başka hiçbir
     * amaçla kullanmıyor (bkz. `data`'nın hem TERMINAL_INPUT hem TERMINAL_OUTPUT için aynı şekilde
     * yeniden kullanılması). Masaüstü uygulamasının native görev paneli, göreve tam prompt metnini
     * ayrı bir kullanıcı-JWT'li REST çağrısı yapmadan, doğrudan bu mesajdan okuyabilsin diye eklendi.
     *
     * `projectId`/`repoUrl`/`defaultBranch` de aynı gerekçeyle, TASK_DISPATCH'te başka hiçbir amaçla
     * kullanılmayan `arguments` (Map) alanına konur — masaüstü, görevin ait olduğu projenin gerçek
     * GitHub reposunu otomatik klonlayıp (bkz. bridge.js ensureProjectCheckout) git/terminal
     * aksiyonlarını ORADA çalıştırabilsin diye (Faz E5). `repoUrl` yoksa (proje repo bilgisi hiç
     * girilmemişse) `arguments` içindeki `repoUrl` de null kalır — runner eski davranışına (tek
     * sabit `projectRoot`) döner.
     */
    public static BridgeMessage dispatch(UUID taskId, String taskType, String title, String prompt,
                                          UUID projectId, String repoUrl, String defaultBranch) {
        Map<String, Object> projectInfo = new HashMap<>();
        projectInfo.put("projectId", projectId == null ? null : projectId.toString());
        projectInfo.put("repoUrl", repoUrl);
        projectInfo.put("defaultBranch", defaultBranch);
        return new BridgeMessage(BridgeMessageType.TASK_DISPATCH, taskId, taskType, title,
                null, null, prompt, null, null, projectInfo, null, null, null, null, null, null);
    }

    /** Bir AI yardım isteğinin cevabı — `toolName` istenen yardımın türünü taşır. */
    public static BridgeMessage aiAssistResult(UUID callId, String kind, boolean success, String text) {
        return new BridgeMessage(BridgeMessageType.AI_ASSIST_RESULT, null, null, null,
                null, null, text, callId, kind, null, success, null, null, null, null, null);
    }

    /** Modelin çağırmak istediği aracı yerel runner'a iletir. */
    public static BridgeMessage toolCall(UUID taskId, UUID callId, String toolName, Map<String, Object> arguments) {
        return new BridgeMessage(BridgeMessageType.TOOL_CALL, taskId, null, null,
                null, null, null, callId, toolName, arguments, null, null, null, null, null, null);
    }

    /** Yerel runner'ın cevabı — success=false ise message alanı hata açıklaması taşır. */
    public static BridgeMessage toolResult(UUID taskId, UUID callId, boolean success, String resultOrError) {
        return new BridgeMessage(BridgeMessageType.TOOL_RESULT, taskId, null, null,
                null, null, resultOrError, callId, null, null, success, null, null, null, null, null);
    }

    public static BridgeMessage terminalOpen(UUID terminalSessionId, int cols, int rows) {
        return new BridgeMessage(BridgeMessageType.TERMINAL_OPEN, null, null, null,
                null, null, null, null, null, null, null, null, terminalSessionId, null, cols, rows);
    }

    public static BridgeMessage terminalInput(UUID terminalSessionId, String data) {
        return new BridgeMessage(BridgeMessageType.TERMINAL_INPUT, null, null, null,
                null, null, null, null, null, null, null, null, terminalSessionId, data, null, null);
    }

    public static BridgeMessage terminalOutput(UUID terminalSessionId, String data) {
        return new BridgeMessage(BridgeMessageType.TERMINAL_OUTPUT, null, null, null,
                null, null, null, null, null, null, null, null, terminalSessionId, data, null, null);
    }

    public static BridgeMessage terminalResize(UUID terminalSessionId, int cols, int rows) {
        return new BridgeMessage(BridgeMessageType.TERMINAL_RESIZE, null, null, null,
                null, null, null, null, null, null, null, null, terminalSessionId, null, cols, rows);
    }

    public static BridgeMessage terminalClose(UUID terminalSessionId) {
        return new BridgeMessage(BridgeMessageType.TERMINAL_CLOSE, null, null, null,
                null, null, null, null, null, null, null, null, terminalSessionId, null, null, null);
    }

    public static BridgeMessage terminalClosed(UUID terminalSessionId) {
        return new BridgeMessage(BridgeMessageType.TERMINAL_CLOSED, null, null, null,
                null, null, null, null, null, null, null, null, terminalSessionId, null, null, null);
    }
}
