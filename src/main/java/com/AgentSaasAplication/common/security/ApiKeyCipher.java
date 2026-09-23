package com.AgentSaasAplication.common.security;

/**
 * Saklanan agent API anahtarlarını (Claude/ChatGPT/Gemini) şifreleyip çözer — anahtarların
 * kendisi asla düz metin olarak DB'de tutulmaz.
 *
 * İki implementasyonu var:
 * <ul>
 *   <li>{@link LocalAesApiKeyCipher} — eski davranışın aynısı, `app.vault.enabled=false`
 *   (varsayılan, `local` profili dahil) iken aktif. Anahtar uygulamanın kendi env var'ında.</li>
 *   <li>{@link VaultApiKeyCipher} — `app.vault.enabled=true` iken aktif. Anahtar hiç uygulamaya
 *   GİRMEZ; şifreleme/çözme işleminin kendisi HashiCorp Vault'un Transit secrets engine'ine
 *   yaptırılır ("encryption as a service") — bu, gerçek bir KMS/Vault migration'ının tam olarak
 *   çözdüğü sorun: uygulama çökse/sızsa bile şifreleme anahtarı hiçbir zaman uygulama sürecinin
 *   belleğinde/ortam değişkeninde bulunmaz.</li>
 * </ul>
 * Hangisinin aktif olduğu tamamen konfigürasyonla belirlenir (bkz. application.yml
 * `app.vault.enabled`) — {@link com.AgentSaasAplication.agent.service.AgentConnectionService} ve
 * connector'lar (ClaudeConnector vb.) bu arayüze bağımlı, hangi implementasyonun aktif olduğunu
 * bilmez/bilmesi gerekmez.
 */
public interface ApiKeyCipher {

    String encrypt(String plaintext);

    String decrypt(String ciphertext);
}
