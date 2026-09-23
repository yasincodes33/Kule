package com.AgentSaasAplication.approval.service;

import com.AgentSaasAplication.approval.domain.ApprovalRequest;
import com.AgentSaasAplication.approval.domain.ApprovalStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Faz 10 — riskli bir araç çağrısı için backend onayı isteyip SONUCU BEKLER. Bu bekleme
 * bilinçli olarak senkron/thread-bloklayan bir tasarım: kod tabanında zaten aynı desen var
 * (BridgeMessageSender.sendToolCallAndWait — git_push için 10 dakikaya kadar aynı şekilde
 * worker thread'i bloke ediyor). "Onay bekleniyor" durumunu bambaşka bir askıya alma/devam
 * ettirme mimarisiyle (konuşma geçmişini kalıcılaştırıp sonra kaldığı yerden devam etmek)
 * çözmek bu fazın kapsamı dışında bırakıldı — bkz. waitTimeout altındaki not.
 *
 * ApprovalService.requestApproval() zaten task'ı AWAITING_APPROVAL'a, approve()/reject() zaten
 * RUNNING/REJECTED'e geçiriyor — bu sınıf yalnızca "ne zaman devam edeceğini" öğreniyor,
 * task'ın durumuna kendisi hiç dokunmuyor.
 */
@Slf4j
@Service
public class ToolCallApprovalGate {

    /**
     * Varsayılan 10dk — git_push/run_command'ın kendi SLOW zaman aşımıyla (StandardTools/
     * DurationClass) tutarlı. ApprovalRequest'in kendi TTL'i (24 saat,
     * ApprovalService.DEFAULT_APPROVAL_TTL) farklı bir şey — o, "bu istek ne zaman kalıcı
     * olarak EXPIRED sayılsın" sorusuna cevap verirken, buradaki süre yalnızca "bu görev
     * çalıştırması onay için worker thread'i ne kadar bekletsin" sorusuna cevap veriyor. Bu
     * süre dolduğunda ApprovalRequest hâlâ PENDING kalır (bir yetkili REST üzerinden daha sonra
     * onaylayabilir/reddedebilir ya da 24 saatlik süpürme onu EXPIRED'a çevirir) — ama BU görev
     * çalıştırması zaten vazgeçmiş olur, otomatik devam etmez (konuşma durumu
     * kalıcılaştırılmadığı için edemez de). Test edilebilirlik için `app.approval.tool-call-
     * wait-timeout-ms` ile yapılandırılabilir.
     */
    /** Onay ekranında gösterilecek tek bir argüman değerinin üst sınırı. */
    private static final int MAX_ARGUMENT_CHARS = 2000;

    private final Duration waitTimeout;
    private final ApprovalService approvalService;
    private final PendingApprovalRegistry pendingApprovalRegistry;

    public ToolCallApprovalGate(ApprovalService approvalService, PendingApprovalRegistry pendingApprovalRegistry,
            @Value("${app.approval.tool-call-wait-timeout-ms:600000}") long waitTimeoutMs) {
        this.approvalService = approvalService;
        this.pendingApprovalRegistry = pendingApprovalRegistry;
        this.waitTimeout = Duration.ofMillis(waitTimeoutMs);
    }

    /**
     * Argümanları onay ekranında okunabilir tek bir metne çevirir. JSON yerine düz
     * "anahtar: değer" satırları kullanılıyor — bu metni bir insan okuyacak, makine değil.
     * Çok uzun değerler (ör. bir dosyanın tamamı) kırpılıyor.
     */
    static String readableArguments(Map<String, Object> arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> entry : arguments.entrySet()) {
            String deger = String.valueOf(entry.getValue());
            if (deger.length() > MAX_ARGUMENT_CHARS) {
                deger = deger.substring(0, MAX_ARGUMENT_CHARS) + "… (kırpıldı)";
            }
            if (sb.length() > 0) {
                sb.append(System.lineSeparator());
            }
            sb.append(entry.getKey()).append(": ").append(deger);
        }
        return sb.toString();
    }

    /**
     * @return boş — onaylandı, çağıran araç çağrısını normal şekilde yürütmeye devam edebilir.
     *         Doluysa — red/zaman aşımı sebebi; çağıran araç çağrısını YÜRÜTMEMELİ.
     */
    public Optional<String> requestAndAwaitDecision(UUID taskId, UUID requestedByUserId, String toolName) {
        return requestAndAwaitDecision(taskId, requestedByUserId, toolName, null);
    }

    /**
     * @param arguments araç çağrısının argümanları; onay ekranında gösterilmek üzere
     *                  okunabilir bir metne çevrilip kaydedilir, böylece onaylayan kişi
     *                  neyi onayladığını görebilir (bkz. V23 migration).
     */
    public Optional<String> requestAndAwaitDecision(UUID taskId, UUID requestedByUserId, String toolName,
            Map<String, Object> arguments) {
        ApprovalRequest request = approvalService.requestApproval(taskId, requestedByUserId, toolName,
                readableArguments(arguments));
        UUID approvalId = request.getId();
        CompletableFuture<ApprovalStatus> future = pendingApprovalRegistry.register(approvalId);

        log.info("Riskli araç çağrısı onay bekliyor: taskId={}, tool={}, approvalId={}, timeout={}",
                taskId, toolName, approvalId, waitTimeout);

        ApprovalStatus outcome;
        try {
            outcome = future.get(waitTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            pendingApprovalRegistry.remove(approvalId);
            log.warn("Onay zaman aşımına uğradı (görev çalıştırması vazgeçiyor, istek hâlâ PENDING): "
                    + "taskId={}, approvalId={}", taskId, approvalId);
            return Optional.of("Onay " + waitTimeout.toSeconds() + " saniye içinde verilmedi");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            pendingApprovalRegistry.remove(approvalId);
            return Optional.of("Onay beklenirken görev çalıştırması kesildi");
        } catch (ExecutionException e) {
            pendingApprovalRegistry.remove(approvalId);
            return Optional.of("Onay bekleme hatası: " + e.getMessage());
        }

        return switch (outcome) {
            case APPROVED -> Optional.empty();
            case REJECTED -> Optional.of("Onay reddedildi");
            case EXPIRED -> Optional.of("Onay süresi doldu");
            case PENDING -> Optional.of("Beklenmeyen durum: onay hâlâ PENDING görünüyor");
        };
    }
}
