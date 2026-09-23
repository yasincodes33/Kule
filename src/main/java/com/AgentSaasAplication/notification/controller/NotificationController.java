package com.AgentSaasAplication.notification.controller;

import com.AgentSaasAplication.common.tenant.CurrentUserResolver;
import com.AgentSaasAplication.notification.domain.Notification;
import com.AgentSaasAplication.notification.dto.NotificationResponse;
import com.AgentSaasAplication.notification.service.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class NotificationController {

    private final NotificationService notificationService;
    private final CurrentUserResolver currentUserResolver;

    public NotificationController(NotificationService notificationService,
                                   CurrentUserResolver currentUserResolver) {
        this.notificationService = notificationService;
        this.currentUserResolver = currentUserResolver;
    }

    /** X-Organization-Id header'ı hangi org'u işaret ediyorsa, o org'daki bildirimler döner (RLS). */
    @GetMapping("/me/notifications")
    public ResponseEntity<Page<NotificationResponse>> listMyNotifications(
            @AuthenticationPrincipal Jwt jwt, Pageable pageable) {
        UUID userId = currentUserResolver.resolve(jwt);
        Page<NotificationResponse> page = notificationService.listMyNotifications(userId, pageable)
                .map(NotificationResponse::from);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/me/notifications/unread-count")
    public ResponseEntity<Map<String, Long>> unreadCount(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = currentUserResolver.resolve(jwt);
        return ResponseEntity.ok(Map.of("count", notificationService.unreadCount(userId)));
    }

    @PostMapping("/me/notifications/{notificationId}/read")
    public ResponseEntity<NotificationResponse> markAsRead(@PathVariable UUID notificationId,
                                                              @AuthenticationPrincipal Jwt jwt) {
        UUID userId = currentUserResolver.resolve(jwt);
        Notification notification = notificationService.markAsRead(notificationId, userId);
        return ResponseEntity.ok(NotificationResponse.from(notification));
    }
}