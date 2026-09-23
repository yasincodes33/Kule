package com.AgentSaasAplication.identity.service;

import com.AgentSaasAplication.common.tenant.CurrentUserResolver;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * JWT'nin "sub" claim'i doğrudan kullanıcının kendi UUID'sidir (bkz.
 * common.security.JwtService): token'ı backend, kullanıcıyı register() içinde
 * oluşturduktan sonra imzalar. Bu yüzden burada ek bir veritabanı sorgusuna gerek yoktur;
 * token'ın imzalanmış olması kullanıcının var olduğunun kanıtıdır.
 */
@Component
public class JwtCurrentUserResolver implements CurrentUserResolver {

    @Override
    public UUID resolve(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
