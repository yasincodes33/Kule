package com.AgentSaasAplication.task.dto;

/** `/ws/tasks/{taskId}/logs` handshake'i için tek kullanımlık, kısa ömürlü
 * bilet — bkz. {@link com.AgentSaasAplication.gateway.WsTicketService}. */
public record WsTicketResponse(String ticket) {
}
