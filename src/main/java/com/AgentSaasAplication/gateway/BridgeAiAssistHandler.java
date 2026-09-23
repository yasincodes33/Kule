package com.AgentSaasAplication.gateway;

import com.AgentSaasAplication.agent.service.AiAssistService;
import com.AgentSaasAplication.common.bridge.BridgeMessage;
import com.AgentSaasAplication.common.bridge.BridgeMessageSender;
import com.AgentSaasAplication.common.domain.ModelTier;
import com.AgentSaasAplication.common.tenant.TenantContext;
import com.AgentSaasAplication.task.service.TaskStateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Masaüstünün bridge üzerinden istediği tek atışlık AI yardımları:
 *
 * <ul>
 *   <li>{@code COMMIT_MESSAGE} — `git diff` verilir, commit mesajı döner (git çubuğundaki düğme).</li>
 *   <li>{@code REVIEW} — `git diff` verilir, kod incelemesi döner; ayrıca göreve kalıcı bir
 *       {@code REVIEW} logu yazılır ("Tamamlandı"dan önceki ikinci göz).</li>
 * </ul>
 *
 * {@code @Async}: iş bridge'in WebSocket mesaj işleyicisinden geliyor ve model çağrısı saniyeler
 * sürebiliyor; o thread'i tutmak aynı runner'ın diğer mesajlarını (heartbeat, terminal çıktısı)
 * geciktirirdi. Async thread TenantContext'i devralmadığı için organizasyon kimliği elle taşınıyor
 * (TaskCreatedEventKafkaBridge'de kanıtlanmış desen).
 */
@Slf4j
@Component
public class BridgeAiAssistHandler {

    private final AiAssistService aiAssistService;
    private final TaskStateService taskStateService;
    private final BridgeMessageSender bridgeMessageSender;

    public BridgeAiAssistHandler(AiAssistService aiAssistService, TaskStateService taskStateService,
                                 BridgeMessageSender bridgeMessageSender) {
        this.aiAssistService = aiAssistService;
        this.taskStateService = taskStateService;
        this.bridgeMessageSender = bridgeMessageSender;
    }

    @Async
    public void handle(UUID organizationId, UUID runnerId, UUID callId, UUID taskId, String kind, String content) {
        TenantContext.set(organizationId);
        try {
            Optional<String> answer = switch (kind == null ? "" : kind) {
                case "COMMIT_MESSAGE" -> commitMessage(content);
                case "REVIEW" -> review(taskId, content);
                default -> Optional.of("Bilinmeyen yardım türü: " + kind);
            };
            boolean ok = answer.isPresent();
            bridgeMessageSender.send(runnerId, BridgeMessage.aiAssistResult(callId, kind, ok,
                    answer.orElse("Bu organizasyonda kullanılabilir bir API ajanı yok ya da çağrı başarısız oldu. "
                            + "Ajanlar sayfasından bir API anahtarı ekleyebilirsin.")));
        } catch (Exception e) {
            log.warn("AI yardım isteği işlenemedi: kind={}, hata={}", kind, e.toString());
            bridgeMessageSender.send(runnerId,
                    BridgeMessage.aiAssistResult(callId, kind, false, "Hata: " + e.getMessage()));
        } finally {
            TenantContext.clear();
        }
    }

    private Optional<String> commitMessage(String diff) {
        String system = """
                Sana bir `git diff` çıktısı veriliyor. Conventional Commits biçiminde TEK bir \
                commit mesajı yaz: ilk satır 72 karakteri geçmeyen bir özet, gerekiyorsa boş satır \
                ve en fazla 3 madde gövde. Yalnızca mesajı yaz — tırnak, kod bloğu, açıklama ekleme. \
                Özet satırı Türkçe olsun.""";
        return aiAssistService.ask(ModelTier.BUDGET, system, diff);
    }

    private Optional<String> review(UUID taskId, String diff) {
        String system = """
                Sana bir `git diff` çıktısı veriliyor. Kıdemli bir geliştirici gibi inceleme yap: \
                yalnızca GERÇEK sorunları yaz (hata, güvenlik açığı, sızdırılan sır, eksik hata \
                yönetimi, kırılan davranış). Her bulgu tek satır ve `dosya: sorun` biçiminde olsun. \
                Sorun yoksa yalnızca "Belirgin bir sorun görünmüyor." yaz. Stil/biçim yorumu yapma. \
                Yanıtı Türkçe ver.""";
        Optional<String> answer = aiAssistService.ask(ModelTier.DEFAULT, system, diff);
        // Kalıcı iz: inceleme, görev kapansa da log akışında durur.
        if (answer.isPresent() && taskId != null) {
            taskStateService.appendLog(taskId, "REVIEW", answer.get());
        }
        return answer;
    }
}
