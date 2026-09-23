package com.AgentSaasAplication.approval.service;

import com.AgentSaasAplication.approval.domain.ApprovalRequest;
import com.AgentSaasAplication.approval.domain.ApprovalStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ToolCallApprovalGate, Faz 10'un riskli araç çağrılarını bloke eden tek noktası —
 * dört olası sonucun (onay/red/timeout/beklenmeyen durum) doğru mesaja/dönüşe eşlendiğinden
 * emin olmak kritik: bir yanlış negatif burada riskli bir işlemin sessizce geçmesine yol açar.
 */
@ExtendWith(MockitoExtension.class)
class ToolCallApprovalGateTest {

    @Mock
    private ApprovalService approvalService;
    @Mock
    private PendingApprovalRegistry pendingApprovalRegistry;

    private ToolCallApprovalGate gate;

    /**
     * getId() burada hep null döner (BaseEntity'nin @GeneratedValue id'si gerçek bir DB insert'i
     * gerektiriyor) — zararsız, çünkü approvalService mock'landığı için gate kodu ne register()'a
     * ne de remove()'a hiçbir zaman gerçek bir id vermek zorunda değil, testler sadece bu null
     * değerin İKİ çağrıda da tutarlı kullanıldığını doğruluyor.
     */
    private ApprovalRequest fakeRequest(UUID taskId) {
        return ApprovalRequest.requestFor(UUID.randomUUID(), taskId, UUID.randomUUID(), Instant.now().plusSeconds(3600));
    }

    @Test
    void onay_verilirse_bos_optional_doner() {
        gate = new ToolCallApprovalGate(approvalService, pendingApprovalRegistry, 5000);
        UUID taskId = UUID.randomUUID();
        UUID requestedBy = UUID.randomUUID();
        ApprovalRequest request = fakeRequest(taskId);
        CompletableFuture<ApprovalStatus> future = CompletableFuture.completedFuture(ApprovalStatus.APPROVED);

        when(approvalService.requestApproval(eq(taskId), eq(requestedBy), any(), any())).thenReturn(request);
        when(pendingApprovalRegistry.register(any())).thenReturn(future);

        var result = gate.requestAndAwaitDecision(taskId, requestedBy, "git_push");

        assertThat(result).isEmpty();
    }

    @Test
    void red_edilirse_onay_reddedildi_mesaji_doner() {
        gate = new ToolCallApprovalGate(approvalService, pendingApprovalRegistry, 5000);
        UUID taskId = UUID.randomUUID();
        UUID requestedBy = UUID.randomUUID();
        ApprovalRequest request = fakeRequest(taskId);
        CompletableFuture<ApprovalStatus> future = CompletableFuture.completedFuture(ApprovalStatus.REJECTED);

        when(approvalService.requestApproval(eq(taskId), eq(requestedBy), any(), any())).thenReturn(request);
        when(pendingApprovalRegistry.register(any())).thenReturn(future);

        var result = gate.requestAndAwaitDecision(taskId, requestedBy, "run_command");

        assertThat(result).contains("Onay reddedildi");
    }

    @Test
    void suresi_dolmus_onay_icin_sure_doldu_mesaji_doner() {
        gate = new ToolCallApprovalGate(approvalService, pendingApprovalRegistry, 5000);
        UUID taskId = UUID.randomUUID();
        UUID requestedBy = UUID.randomUUID();
        ApprovalRequest request = fakeRequest(taskId);
        CompletableFuture<ApprovalStatus> future = CompletableFuture.completedFuture(ApprovalStatus.EXPIRED);

        when(approvalService.requestApproval(eq(taskId), eq(requestedBy), any(), any())).thenReturn(request);
        when(pendingApprovalRegistry.register(any())).thenReturn(future);

        var result = gate.requestAndAwaitDecision(taskId, requestedBy, "write_file");

        assertThat(result).contains("Onay süresi doldu");
    }

    @Test
    void beklenmeyen_pending_durumu_icin_acik_bir_mesaj_doner() {
        gate = new ToolCallApprovalGate(approvalService, pendingApprovalRegistry, 5000);
        UUID taskId = UUID.randomUUID();
        UUID requestedBy = UUID.randomUUID();
        ApprovalRequest request = fakeRequest(taskId);
        CompletableFuture<ApprovalStatus> future = CompletableFuture.completedFuture(ApprovalStatus.PENDING);

        when(approvalService.requestApproval(eq(taskId), eq(requestedBy), any(), any())).thenReturn(request);
        when(pendingApprovalRegistry.register(any())).thenReturn(future);

        var result = gate.requestAndAwaitDecision(taskId, requestedBy, "git_push");

        assertThat(result).isPresent();
        assertThat(result.get()).contains("Beklenmeyen durum");
    }

    @Test
    void zaman_asimina_ugrarsa_kayit_kaldirilir_ve_sure_mesaji_doner() {
        // Çok kısa bir timeout ile gerçek bir CompletableFuture kullanılıyor — asla tamamlanmayan
        // bir future, gerçek TimeoutException'ı tetikliyor (mock'lanmış bir davranış değil).
        gate = new ToolCallApprovalGate(approvalService, pendingApprovalRegistry, 50);
        UUID taskId = UUID.randomUUID();
        UUID requestedBy = UUID.randomUUID();
        ApprovalRequest request = fakeRequest(taskId);
        CompletableFuture<ApprovalStatus> neverCompletes = new CompletableFuture<>();

        when(approvalService.requestApproval(eq(taskId), eq(requestedBy), any(), any())).thenReturn(request);
        when(pendingApprovalRegistry.register(any())).thenReturn(neverCompletes);

        var result = gate.requestAndAwaitDecision(taskId, requestedBy, "git_push");

        assertThat(result).isPresent();
        assertThat(result.get()).contains("saniye içinde verilmedi");
        verify(pendingApprovalRegistry).remove(request.getId());
    }

    @Test
    void future_calisma_hatasiyla_tamamlanirsa_kayit_kaldirilir_ve_hata_mesaji_doner() {
        gate = new ToolCallApprovalGate(approvalService, pendingApprovalRegistry, 5000);
        UUID taskId = UUID.randomUUID();
        UUID requestedBy = UUID.randomUUID();
        ApprovalRequest request = fakeRequest(taskId);
        CompletableFuture<ApprovalStatus> failing = new CompletableFuture<>();
        failing.completeExceptionally(new RuntimeException("beklenmeyen hata"));

        when(approvalService.requestApproval(eq(taskId), eq(requestedBy), any(), any())).thenReturn(request);
        when(pendingApprovalRegistry.register(any())).thenReturn(failing);

        var result = gate.requestAndAwaitDecision(taskId, requestedBy, "git_push");

        assertThat(result).isPresent();
        assertThat(result.get()).contains("Onay bekleme hatası");
        verify(pendingApprovalRegistry).remove(request.getId());
    }

    /**
     * Onay ekrani, onaylayan kisiye NEYI onayladigini gostermek zorunda. Bu test, arac adinin ve
     * argumanlarinin ApprovalService'e GERCEKTEN iletildigini kanitliyor: yalnizca `toolName`
     * yalnizca bir log satirina yaziliyor, kayda hic girmiyordu.
     */
    @Test
    void arac_adi_ve_argumanlari_onay_kaydina_iletilir() {
        gate = new ToolCallApprovalGate(approvalService, pendingApprovalRegistry, 5000);
        UUID taskId = UUID.randomUUID();
        UUID requestedBy = UUID.randomUUID();
        ApprovalRequest request = fakeRequest(taskId);

        when(approvalService.requestApproval(eq(taskId), eq(requestedBy), any(), any())).thenReturn(request);
        when(pendingApprovalRegistry.register(any()))
                .thenReturn(CompletableFuture.completedFuture(ApprovalStatus.APPROVED));

        gate.requestAndAwaitDecision(taskId, requestedBy, "run_command",
                java.util.Map.of("command", "rm -rf build"));

        verify(approvalService).requestApproval(taskId, requestedBy, "run_command", "command: rm -rf build");
    }

    @Test
    void cok_uzun_arguman_degeri_kirpilir() {
        String uzun = "x".repeat(5000);

        String okunabilir = ToolCallApprovalGate.readableArguments(java.util.Map.of("content", uzun));

        assertThat(okunabilir).hasSizeLessThan(2200);
        assertThat(okunabilir).contains("content: ");
        assertThat(okunabilir).endsWith("(kırpıldı)");
    }

    @Test
    void arguman_yoksa_null_doner() {
        assertThat(ToolCallApprovalGate.readableArguments(null)).isNull();
        assertThat(ToolCallApprovalGate.readableArguments(java.util.Map.of())).isNull();
    }
}
