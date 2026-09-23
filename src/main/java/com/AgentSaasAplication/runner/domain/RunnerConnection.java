package com.AgentSaasAplication.runner.domain;

import com.AgentSaasAplication.common.entity.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Bir kullanıcının kendi makinesinde çalıştırdığı yerel runner bağlantısı.
 *
 * agent_connections'tan bilinçli olarak ayrıldı: orası organizasyon seviyesindeki bulut
 * sağlayıcılarını (Claude/ChatGPT/Gemini API anahtarları) tutuyor, burası ise kişiye ait
 * makineleri. İkisi farklı sahiplik ve yaşam döngüsüne sahip, tek tabloda tutulmaları
 * ownerUserId alanının kayıtların yarısında anlamsız kalmasına yol açıyordu.
 *
 * Araç tipi (agentType) burada YOK — kullanıcı görevi hangi araçla çözeceğine kendi
 * terminalinde karar veriyor, sonucu bildirirken opsiyonel olarak raporluyor.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "runner_connections")
public class RunnerConnection extends TenantScopedEntity {

    /** Runner'ı çalıştıran çalışan. Görev ataması bu alanla eşleşiyor. */
    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    /**
     * Runner'ın diskindeki çalışma kopyası hangi projeye aitse o. Nullable:
     * dolu ise runner yalnızca o projenin görevlerini alır, boş ise organizasyondaki
     * her projenin görevine açıktır.
     */
    @Column(name = "project_id")
    private UUID projectId;

    /** Kullanıcının kendi runner'larını ayırt etmesi için ("iş bilgisayarı", "ev"). */
    @Column(name = "label")
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RunnerStatus status;

    @Column(name = "last_heartbeat_at")
    private Instant lastHeartbeatAt;

    @Column(name = "bridge_token_hash")
    private String bridgeTokenHash;

    @Column(name = "bridge_token_issued_at")
    private Instant bridgeTokenIssuedAt;

    private RunnerConnection(UUID organizationId, UUID ownerUserId, UUID projectId, String label) {
        super(organizationId);
        this.ownerUserId = ownerUserId;
        this.projectId = projectId;
        this.label = label;
        this.status = RunnerStatus.OFFLINE;
    }

    public static RunnerConnection register(UUID organizationId, UUID ownerUserId, UUID projectId, String label) {
        return new RunnerConnection(organizationId, ownerUserId, projectId, label);
    }

    public void issueBridgeToken(String tokenHash) {
        this.bridgeTokenHash = tokenHash;
        this.bridgeTokenIssuedAt = Instant.now();
    }

    public void revokeBridgeToken() {
        this.bridgeTokenHash = null;
        this.bridgeTokenIssuedAt = null;
    }

    public void recordHeartbeat() {
        this.status = RunnerStatus.ONLINE;
        this.lastHeartbeatAt = Instant.now();
    }

    public void markOffline() {
        this.status = RunnerStatus.OFFLINE;
    }

    public boolean isOnline() {
        return this.status == RunnerStatus.ONLINE;
    }

    /** projectId boşsa runner her projeye açık; doluysa yalnızca o projeye. */
    public boolean servesProject(UUID candidateProjectId) {
        return this.projectId == null || this.projectId.equals(candidateProjectId);
    }
}
