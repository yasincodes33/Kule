package com.AgentSaasAplication.identity.repository;

import com.AgentSaasAplication.identity.domain.RefreshTokenRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRecordRepository extends JpaRepository<RefreshTokenRecord, UUID> {
    Optional<RefreshTokenRecord> findByJti(String jti);
    List<RefreshTokenRecord> findByUserIdAndRevokedAtIsNull(UUID userId);

    /** Bu tabloyu hiçbir şey temizlemiyordu — her
     * `/auth/refresh` yeni bir satır ekleyip eskisini revoke ediyor, hiçbiri silinmiyordu. Bir
     * satır artık işe yaramaz olur olmaz (revoke edildi YA DA süresi doğal olarak doldu) belirli
     * bir süre sonra silinebilir — bkz. RefreshTokenMaintenanceService. */
    long deleteByRevokedAtBeforeOrExpiresAtBefore(Instant revokedBefore, Instant expiresBefore);
}
