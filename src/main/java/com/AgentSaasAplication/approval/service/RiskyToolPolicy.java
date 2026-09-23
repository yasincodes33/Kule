package com.AgentSaasAplication.approval.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Hangi araç çağrılarının backend onayı gerektirdiğini belirler (Faz 10 — üç katmanlı güvenlik
 * modelinin 2. katmanı). Yalnızca AbstractApiAgentConnector.executeToolCall() üzerinden geçen
 * (bulut ajanlarının — Claude/ChatGPT/Gemini — kendi karar verdiği) araç çağrıları bu kapsamda;
 * bir görev tamamen Hermes/Claude Code/Antigravity'ye devredilmişse bu sınıfın hiç görmediği bir
 * yoldan gidiyor (bilinçli sınır — 3. katman, faz planına bakın).
 *
 * git_push ve run_command HER ZAMAN riskli — geri alınamaz (push) ya da keyfi kod çalıştırma
 * (command) oldukları için. write_file yalnızca "korumalı" bir path'e yazıyorsa riskli — örn.
 * CI/CD config'i, bağımlılık manifestosu, ortam dosyası. Liste `application.yml`'den
 * yapılandırılabilir; boşsa yalnızca git_push/run_command riskli sayılır.
 */
@Component
public class RiskyToolPolicy {

    private static final Set<String> ALWAYS_RISKY = Set.of("git_push", "run_command");
    private static final String WRITE_FILE = "write_file";

    private final List<String> protectedWritePathPatterns;

    public RiskyToolPolicy(
            @Value("${app.approval.protected-write-paths:.github/,.env,pom.xml,application.yml,application.properties}")
            List<String> protectedWritePathPatterns) {
        this.protectedWritePathPatterns = protectedWritePathPatterns;
    }

    public boolean requiresApproval(String toolName, Map<String, Object> arguments) {
        if (ALWAYS_RISKY.contains(toolName)) {
            return true;
        }
        if (WRITE_FILE.equals(toolName)) {
            return isProtectedWritePath(arguments);
        }
        return false;
    }

    private boolean isProtectedWritePath(Map<String, Object> arguments) {
        Object pathValue = arguments == null ? null : arguments.get("path");
        if (!(pathValue instanceof String path) || path.isBlank()) {
            return false;
        }
        String normalized = path.replace('\\', '/');
        return protectedWritePathPatterns.stream().anyMatch(pattern -> matches(normalized, pattern));
    }

    // Büyük/küçük harf DUYARSIZ karşılaştırma zorunlu: Windows/macOS gibi
    // case-insensitive dosya sistemlerinde "Pom.Xml" ile "pom.xml" AYNI dosyayı işaret
    // eder. Duyarlı bir karşılaştırmada, path'i farklı harf büyüklüğüyle veren bir ajan
    // onay kapısını tamamen atlayabilir. Locale.ROOT ŞART: sistem locale'i Türkçe
    // olduğunda toLowerCase() "I" harfini "ı" (noktasız) yapar ve "application.yml"
    // pattern'indeki ASCII 'i' ile eşleşmez.
    private boolean matches(String path, String pattern) {
        String normalizedPath = path.toLowerCase(Locale.ROOT);
        String normalizedPattern = pattern.replace('\\', '/').toLowerCase(Locale.ROOT);
        return normalizedPath.equals(normalizedPattern)
                || normalizedPath.startsWith(normalizedPattern)
                || normalizedPath.endsWith("/" + normalizedPattern);
    }
}
