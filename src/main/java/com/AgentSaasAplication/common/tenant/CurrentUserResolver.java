package com.AgentSaasAplication.common.tenant;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

public interface CurrentUserResolver {
    UUID resolve(Jwt jwt);
}