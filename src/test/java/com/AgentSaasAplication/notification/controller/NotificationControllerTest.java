package com.AgentSaasAplication.notification.controller;

import com.AgentSaasAplication.common.exceptions.GlobalExceptionHandler;
import com.AgentSaasAplication.common.tenant.CurrentUserResolver;
import com.AgentSaasAplication.notification.domain.Notification;
import com.AgentSaasAplication.notification.domain.NotificationType;
import com.AgentSaasAplication.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock private NotificationService notificationService;
    @Mock private CurrentUserResolver currentUserResolver;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        NotificationController controller = new NotificationController(notificationService, currentUserResolver);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(
                        new AuthenticationPrincipalArgumentResolver(), new PageableHandlerMethodArgumentResolver())
                .build();
    }

    // Not: liste uç noktasının (Page<NotificationResponse> döndüren) doğrudan testi burada YOK —
    // standaloneSetup'ta Spring Data'nın Page Jackson modülü olmadan Page.empty() Jackson 3 ile
    // serileştirilemiyor (tam context'te SpringDataWebAutoConfiguration bunu çözüyor — bkz.
    // AuditControllerTest'teki aynı not). markAsRead ve unreadCount aşağıda controller wiring'ini
    // zaten kanıtlıyor.

    @Test
    void okunmus_isaretleme_dogru_bildirimi_donuyor() throws Exception {
        when(currentUserResolver.resolve(any())).thenReturn(UUID.randomUUID());
        Notification notification = Notification.create(
                UUID.randomUUID(), UUID.randomUUID(), NotificationType.TASK_COMPLETED, Map.of("taskId", "x"));
        when(notificationService.markAsRead(any(), any())).thenReturn(notification);

        mockMvc.perform(post("/api/v1/me/notifications/{notificationId}/read", UUID.randomUUID()).with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("TASK_COMPLETED"));
    }

    @Test
    void okunmamis_sayisi_dogru_alanda_donuyor() throws Exception {
        when(currentUserResolver.resolve(any())).thenReturn(UUID.randomUUID());
        when(notificationService.unreadCount(any())).thenReturn(5L);

        mockMvc.perform(get("/api/v1/me/notifications/unread-count").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(5));
    }
}
