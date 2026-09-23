package com.AgentSaasAplication.runner.dto;

import java.util.UUID;

/** bkz. RunnerTerminalSessionController.issueTerminalWsTicket — `taskId` opsiyonel, yalnızca
 * görev detay sayfasından "bu araçla başlat" ile açılan oturumlarda gönderilir (terminal
 * çıktısının hangi görevin log akışına yazılacağını belirlemek için, bkz. RunnerTerminalSessionRegistry.linkTask). */
public record IssueTerminalTicketRequest(UUID taskId) {
}
