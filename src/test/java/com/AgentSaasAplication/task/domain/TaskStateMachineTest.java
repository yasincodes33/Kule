package com.AgentSaasAplication.task.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Backend'in ilk gerçek unit test paketi — bu proje boyunca Java tarafında hiç test
 * yoktu (yalnızca Spring Boot'un ürettiği boş contextLoads() placeholder'ı vardı).
 * TaskStateMachine, saf/statik bir yardımcı sınıf olduğu için (Spring context'i gerektirmeden)
 * en yüksek değer/en düşük efor testi — Faz 8/10/12'de bulunan hataların hepsi bu haritanın
 * (ALLOWED) gözden kaçırılmasından kaynaklanmıştı.
 */
class TaskStateMachineTest {

    private static final Set<TaskStatus> TERMINAL = EnumSet.of(
            TaskStatus.COMPLETED, TaskStatus.REJECTED, TaskStatus.CANCELLED);

    @Test
    void queued_yalnizca_dispatched_cancelled_veya_failed_e_gecebilir() {
        assertThat(TaskStateMachine.canTransition(TaskStatus.QUEUED, TaskStatus.DISPATCHED)).isTrue();
        assertThat(TaskStateMachine.canTransition(TaskStatus.QUEUED, TaskStatus.CANCELLED)).isTrue();
        assertThat(TaskStateMachine.canTransition(TaskStatus.QUEUED, TaskStatus.FAILED)).isTrue();
        assertThat(TaskStateMachine.canTransition(TaskStatus.QUEUED, TaskStatus.RUNNING)).isFalse();
        assertThat(TaskStateMachine.canTransition(TaskStatus.QUEUED, TaskStatus.COMPLETED)).isFalse();
        assertThat(TaskStateMachine.canTransition(TaskStatus.QUEUED, TaskStatus.AWAITING_APPROVAL)).isFalse();
    }

    @Test
    void dispatched_yalnizca_running_failed_veya_cancelled_a_gecebilir() {
        assertThat(TaskStateMachine.canTransition(TaskStatus.DISPATCHED, TaskStatus.RUNNING)).isTrue();
        assertThat(TaskStateMachine.canTransition(TaskStatus.DISPATCHED, TaskStatus.FAILED)).isTrue();
        assertThat(TaskStateMachine.canTransition(TaskStatus.DISPATCHED, TaskStatus.CANCELLED)).isTrue();
        assertThat(TaskStateMachine.canTransition(TaskStatus.DISPATCHED, TaskStatus.COMPLETED)).isFalse();
        assertThat(TaskStateMachine.canTransition(TaskStatus.DISPATCHED, TaskStatus.QUEUED)).isFalse();
    }

    @Test
    void running_dan_dogrudan_completed_a_gecis_yok_once_dispatched_dan_gecmesi_gerekir() {
        // DISPATCHED -> COMPLETED doğrudan geçişi yok; AgentBridgeHandler.handleTaskResult()
        // önce şeffafça RUNNING'e alıyor.
        assertThat(TaskStateMachine.canTransition(TaskStatus.DISPATCHED, TaskStatus.COMPLETED)).isFalse();
        assertThat(TaskStateMachine.canTransition(TaskStatus.RUNNING, TaskStatus.COMPLETED)).isTrue();
    }

    @Test
    void running_riskli_arac_cagrisiyla_awaiting_approval_a_gecebilir() {
        assertThat(TaskStateMachine.canTransition(TaskStatus.RUNNING, TaskStatus.AWAITING_APPROVAL)).isTrue();
    }

    @Test
    void awaiting_approval_dan_yalnizca_running_veya_rejected_e_gecilebilir() {
        // Faz 10'un kalbi: bu metodun FAILED'i buradan kabul ETMEMESİ gerekiyor — Faz 10 diag
        // testinde tam olarak bu yüzden bir ApprovalDeniedException/markFailed() kombinasyonu
        // yerine executeTask()'ın markFailed() çağırmadan sessizce dönmesi seçilmişti.
        assertThat(TaskStateMachine.canTransition(TaskStatus.AWAITING_APPROVAL, TaskStatus.RUNNING)).isTrue();
        assertThat(TaskStateMachine.canTransition(TaskStatus.AWAITING_APPROVAL, TaskStatus.REJECTED)).isTrue();
        assertThat(TaskStateMachine.canTransition(TaskStatus.AWAITING_APPROVAL, TaskStatus.FAILED)).isFalse();
        assertThat(TaskStateMachine.canTransition(TaskStatus.AWAITING_APPROVAL, TaskStatus.CANCELLED)).isFalse();
    }

    @Test
    void failed_yalnizca_queued_a_donebilir_bu_retry_in_tek_yolu() {
        assertThat(TaskStateMachine.canTransition(TaskStatus.FAILED, TaskStatus.QUEUED)).isTrue();
        assertThat(TaskStateMachine.canTransition(TaskStatus.FAILED, TaskStatus.DISPATCHED)).isFalse();
        assertThat(TaskStateMachine.canTransition(TaskStatus.FAILED, TaskStatus.RUNNING)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = {"COMPLETED", "REJECTED", "CANCELLED"})
    void terminal_durumlardan_hicbir_yere_gecilemez(TaskStatus terminal) {
        assertThat(TaskStateMachine.isTerminal(terminal)).isTrue();
        for (TaskStatus target : TaskStatus.values()) {
            assertThat(TaskStateMachine.canTransition(terminal, target))
                    .as("%s -> %s izin verilmemeli", terminal, target)
                    .isFalse();
        }
    }

    @Test
    void terminal_olmayan_durumlar_dogru_siniflandiriliyor() {
        for (TaskStatus status : TaskStatus.values()) {
            boolean expectedTerminal = TERMINAL.contains(status);
            assertThat(TaskStateMachine.isTerminal(status))
                    .as("isTerminal(%s)", status)
                    .isEqualTo(expectedTerminal);
        }
    }

    @Test
    void bilinmeyen_bir_hedefe_gecis_sessizce_false_doner_exception_atmaz() {
        // canTransition() bir doğrulama yardımcısı — kararı verecek olan (ve exception atacak
        // olan) Task.transitionTo(), bu metod değil.
        assertThat(TaskStateMachine.canTransition(TaskStatus.QUEUED, TaskStatus.QUEUED)).isFalse();
    }
}
