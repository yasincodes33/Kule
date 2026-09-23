package com.AgentSaasAplication.identity.domain;


import com.AgentSaasAplication.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "organizations")
public class Organization extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    private Organization(String name, String slug) {
        this.name = name;
        this.slug = slug;
    }

    public static Organization create(String name, String slug) {
        return new Organization(name, slug);
    }
}