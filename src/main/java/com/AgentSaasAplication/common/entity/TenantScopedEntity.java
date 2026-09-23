package com.AgentSaasAplication.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@MappedSuperclass
public abstract class TenantScopedEntity extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID tenantId;

    protected TenantScopedEntity(UUID tenantId) {
        this.tenantId = tenantId;
    }
}