package com.AgentSaasAplication.notification.repository;

import com.AgentSaasAplication.notification.domain.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    Page<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
    long countByUserIdAndReadAtIsNull(UUID userId);

    /** `notifications` için hiç temizlik yoktu —
     * okunmuş bildirimlerin süresiz birikmesinin hiçbir değeri yok. `read_at < cutoff` koşulu,
     * SQL'de NULL karşılaştırmaları hep UNKNOWN/false olduğu için okunmamış (read_at IS NULL)
     * bildirileri zaten doğal olarak dışarıda bırakıyor — ayrıca bir "read_at not null" koşuluna
     * gerek yok. `organization_id` filtresi yok çünkü RLS (bkz. V1 migration) zaten
     * TenantContext'e göre satırları filtreliyor — bkz. NotificationRetentionScheduler. */
    long deleteByReadAtBefore(Instant cutoff);
}