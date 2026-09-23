package com.AgentSaasAplication.agent.domain;

import com.AgentSaasAplication.common.entity.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Organizasyon seviyesinde bir bulut model sağlayıcısı kaydı (Claude/ChatGPT/Gemini).
 * Kalıcı bir bağlantı yok — doğrulanmış API anahtarı "hazır" demek, o yüzden kayıt
 * anında ONLINE.
 *
 * Kullanıcıların kendi makinelerindeki runner'lar bu tabloda DEĞİL:
 * {@link com.AgentSaasAplication.runner.domain.RunnerConnection}. İkisi V15'te ayrıldı,
 * çünkü tek tabloda tutulduklarında sahiplik (owner_user_id) ve API anahtarı alanları
 * kayıtların yarısında anlamsız kalıyordu.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "agent_connections")
public class AgentConnection extends TenantScopedEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "agent_type", nullable = false)
    private AgentType agentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AgentStatus status;

    @Column(name = "api_key_encrypted", nullable = false)
    private String apiKeyEncrypted;

    private AgentConnection(UUID organizationId, AgentType agentType, String encryptedApiKey) {
        super(organizationId);
        this.agentType = agentType;
        this.apiKeyEncrypted = encryptedApiKey;
        this.status = AgentStatus.ONLINE;
    }

    public static AgentConnection register(UUID organizationId, AgentType agentType, String encryptedApiKey) {
        return new AgentConnection(organizationId, agentType, encryptedApiKey);
    }

    public void markOffline() {
        this.status = AgentStatus.OFFLINE;
    }

    public void markOnline() {
        this.status = AgentStatus.ONLINE;
    }

    public boolean isOnline() {
        return this.status == AgentStatus.ONLINE;
    }
}
