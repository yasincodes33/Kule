package com.AgentSaasAplication.identity.dto;

/**
 * Refresh token artık JSON body'de dönmüyor — httpOnly cookie olarak set
 * ediliyor (bkz. AuthController). Bu, dışarı (frontend'e) dönen gerçek yanıt gövdesi; refresh
 * token'ı da taşıyan iç {@link AuthResponse} yalnızca servis katmanı ile controller arasında
 * kalıyor.
 */
public record AuthTokenResponse(String accessToken, long expiresIn) {
    public static AuthTokenResponse from(AuthResponse response) {
        return new AuthTokenResponse(response.accessToken(), response.expiresIn());
    }
}
