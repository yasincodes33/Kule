package com.AgentSaasAplication.common.bridge;

public enum BridgeMessageType {
    HEARTBEAT,
    TASK_DISPATCH,
    TASK_RESULT,
    LOG,
    TOOL_CALL,
    TOOL_RESULT,
    /** Backend'den runner'a: canlı terminal oturumu aç (bkz. RunnerTerminalWsHandler). */
    TERMINAL_OPEN,
    /** Backend'den runner'a: tarayıcıdan gelen tuş girişini shell'e ilet. */
    TERMINAL_INPUT,
    /** Runner'dan backend'e: shell çıktısını tarayıcıya ilet. */
    TERMINAL_OUTPUT,
    /** Backend'den runner'a: terminal boyutu değişti (non-PTY modda no-op). */
    TERMINAL_RESIZE,
    /** Backend'den runner'a: oturumu kapat (tarayıcı bağlantısı koptu). */
    TERMINAL_CLOSE,
    /** Runner'dan backend'e: shell process'i kendiliğinden sona erdi (örn. kullanıcı "exit" yazdı). */
    TERMINAL_CLOSED,
    /** Runner'dan backend'e: masaüstü uygulamasının native görev panelinde prompt düzenlendi
     * (bkz. TaskOrchestrationService.updatePrompt) — kullanıcı-JWT'siz, bridge-token'lı yol. */
    TASK_PROMPT_UPDATE,
    /** Runner'dan backend'e: bulut modelinden tek atışlık bir yardım iste
     * (commit mesajı üretimi, kod incelemesi). Masaüstünün kullanıcı-JWT'si olmadığı için
     * REST yerine bridge üzerinden akıyor — TASK_RESULT/TASK_PROMPT_UPDATE ile aynı gerekçe. */
    AI_ASSIST_REQUEST,
    /** Backend'den runner'a: yukarıdaki isteğin cevabı (callId ile eşleşir). */
    AI_ASSIST_RESULT
}