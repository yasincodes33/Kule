package com.AgentSaasAplication.notification.dto;

import com.AgentSaasAplication.notification.domain.Notification;
import com.AgentSaasAplication.notification.domain.NotificationType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        NotificationType type,
        Map<String, Object> payload,
        boolean read,
        Instant createdAt
) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getPayload(),
                notification.isRead(),
                notification.getCreatedAt()
        );
    }
}