package com.AgentSaasAplication.notification.service;

import com.AgentSaasAplication.common.exceptions.NotFoundException;
import com.AgentSaasAplication.notification.domain.Notification;
import com.AgentSaasAplication.notification.domain.NotificationType;
import com.AgentSaasAplication.notification.repository.NotificationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public void create(UUID organizationId, UUID userId, NotificationType type, Map<String, Object> payload) {
        Notification notification = Notification.create(organizationId, userId, type, payload);
        notificationRepository.save(notification);
        log.info("Bildirim oluşturuldu: type={}, userId={}, organizationId={}", type, userId, organizationId);
    }

    public Page<Notification> listMyNotifications(UUID userId, Pageable pageable) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    public long unreadCount(UUID userId) {
        return notificationRepository.countByUserIdAndReadAtIsNull(userId);
    }

    @Transactional
    public Notification markAsRead(UUID notificationId, UUID userId) {
    	Notification notification = notificationRepository.findById(notificationId)
    	        .orElseThrow(() -> new NotFoundException("Bildirim bulunamadı: " + notificationId));

        if (!notification.getUserId().equals(userId)) {
            throw new AccessDeniedException("Bu bildirim size ait değil");
        }

        notification.markAsRead();
        return notification;
    }

    /** Okunmuş, belirli bir süreden eski
     * bildirimleri temizler — bkz. NotificationRetentionScheduler. Yalnızca aktif TenantContext'in
     * organizasyonunu etkiler (RLS). */
    @Transactional
    public long purgeOldRead(Instant cutoff) {
        return notificationRepository.deleteByReadAtBefore(cutoff);
    }
}