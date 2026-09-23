package com.AgentSaasAplication.common.security;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * `ApiKeyCipher`'ın HashiCorp Vault tabanlı uygulaması. Şifreleme anahtarı
 * (`app.jwt.secret`'in aksine) uygulamanın hiçbir yerinde — ne bellekte ne ortam
 * değişkeninde — bulunmaz: şifreleme/çözme işleminin kendisi Vault'un Transit secrets
 * engine'ine ("encryption as a service") yaptırılır. Uygulama yalnızca bir
 * `X-Vault-Token` taşıyor — bu token'ın kendisi de şifreleme anahtarı DEĞİL, yalnızca Vault'a
 * "bu transit key'i kullanmama izin ver" diyen bir erişim kimlik bilgisi (gerçek bir dağıtımda bu
 * token da kısa ömürlü olmalı — AppRole/Kubernetes auth gibi bir yöntemle alınır; burada basit bir
 * statik dev-root-token kullanılıyor, bkz. docker/docker-compose.yml'deki `vault` servisi).
 *
 * `app.vault.enabled=true` iken aktif (yerelde varsayılan false — bkz. LocalAesApiKeyCipher).
 *
 * Bilinen sınırlama: şifreleme ŞEMASI (Vault'un kendi `vault:v1:...` formatı)
 * `LocalAesApiKeyCipher`'ınkinden (ham AES/GCM+IV, Base64) TAMAMEN farklı — bu iki cipher arasında
 * geçiş, DB'de zaten `LocalAesApiKeyCipher`'la şifrelenmiş satırlar varsa onları YENİDEN
 * şifrelemeyi gerektirir (bu class'ın decrypt()'i eski formatı çözemez). Bu, gerçek bir cutover'da
 * ayrı bir migration script'i gerektiren, kasıtlı olarak bu turun kapsamı dışında bırakılan bir
 * operasyonel adım.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.vault", name = "enabled", havingValue = "true")
public class VaultApiKeyCipher implements ApiKeyCipher {

    private final RestClient restClient;
    private final String transitKeyName;

    public VaultApiKeyCipher(RestClient.Builder restClientBuilder,
                              @Value("${app.vault.address:http://localhost:8200}") String vaultAddress,
                              @Value("${app.vault.token}") String vaultToken,
                              @Value("${app.vault.transit-key-name:agentsaas-api-keys}") String transitKeyName) {
        this.transitKeyName = transitKeyName;
        this.restClient = restClientBuilder
                .baseUrl(vaultAddress)
                .defaultHeader("X-Vault-Token", vaultToken)
                .build();
    }

    /** Uygulama başlarken (a) transit secrets engine'in mount edildiğinden, (b) transit key'in
     * var olduğundan emin olur — ikisi de yoksa oluşturur (idempotent). Vault'a hiç erişilemezse
     * fail-fast — API_KEY_SECRET'in eksikliğinde olduğu gibi, şifreleme çalışmadan uygulamanın
     * ayağa kalkması yanıltıcı olurdu. */
    @PostConstruct
    void ensureVaultReady() {
        ensureTransitEngineMounted();
        ensureTransitKeyExists();
    }

    /** Vault'un dev modu transit secrets engine'ini otomatik mount ETMEZ (yalnızca
     * `secret/` kv engine'i mount edilmiş gelir). Mount edilmemişken
     * `/v1/transit/keys/...`'e istek atmak "no handler for route" ile 404 döner; bu da
     * yanlışlıkla "key henüz yok" sanılmasına yol açar. Bu yüzden mount durumu, hata
     * mesajı metnini ayrıştırmak yerine her zaman var olan `/v1/sys/mounts` sistem uç
     * noktasından kontrol ediliyor.
     */
    private void ensureTransitEngineMounted() {
        Map<?, ?> mounts = restClient.get().uri("/v1/sys/mounts").retrieve().body(Map.class);
        if (mounts != null && mounts.containsKey("transit/")) {
            return;
        }
        restClient.post().uri("/v1/sys/mounts/transit").body(Map.of("type", "transit")).retrieve().toBodilessEntity();
        log.info("Vault transit secrets engine mount edildi");
    }

    private void ensureTransitKeyExists() {
        boolean exists;
        try {
            restClient.get().uri("/v1/transit/keys/{name}", transitKeyName).retrieve().toBodilessEntity();
            exists = true;
        } catch (RestClientResponseException e) {
            if (e.getStatusCode() == HttpStatusCode.valueOf(404)) {
                exists = false;
            } else {
                throw new IllegalStateException("Vault transit key durumu kontrol edilemedi: " + transitKeyName, e);
            }
        }

        if (!exists) {
            restClient.post().uri("/v1/transit/keys/{name}", transitKeyName).retrieve().toBodilessEntity();
            log.info("Vault transit key oluşturuldu: {}", transitKeyName);
        }
    }

    @Override
    public String encrypt(String plaintext) {
        String base64Plaintext = Base64.getEncoder().encodeToString(plaintext.getBytes(StandardCharsets.UTF_8));
        try {
            EncryptResponse response = restClient.post()
                    .uri("/v1/transit/encrypt/{name}", transitKeyName)
                    .body(Map.of("plaintext", base64Plaintext))
                    .retrieve()
                    .body(EncryptResponse.class);
            if (response == null || response.data() == null || response.data().ciphertext() == null) {
                throw new IllegalStateException("Vault boş/eksik bir şifreleme yanıtı döndürdü");
            }
            return response.data().ciphertext();
        } catch (RestClientResponseException e) {
            throw new IllegalStateException("API key Vault ile şifrelenemedi", e);
        }
    }

    @Override
    public String decrypt(String ciphertext) {
        try {
            DecryptResponse response = restClient.post()
                    .uri("/v1/transit/decrypt/{name}", transitKeyName)
                    .body(Map.of("ciphertext", ciphertext))
                    .retrieve()
                    .body(DecryptResponse.class);
            if (response == null || response.data() == null || response.data().plaintext() == null) {
                throw new IllegalStateException("Vault boş/eksik bir çözme yanıtı döndürdü");
            }
            return new String(Base64.getDecoder().decode(response.data().plaintext()), StandardCharsets.UTF_8);
        } catch (RestClientResponseException e) {
            throw new IllegalStateException("API key Vault ile çözülemedi", e);
        }
    }

    private record EncryptResponse(EncryptData data) {
    }

    private record EncryptData(String ciphertext) {
    }

    private record DecryptResponse(DecryptData data) {
    }

    private record DecryptData(String plaintext) {
    }
}
