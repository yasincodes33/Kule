package com.AgentSaasAplication.runner.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * servesProject(), RunnerLookupService'in "bu runner bu görevi alabilir mi" kararının
 * altında yatan tek kural — bir projeye kilitli bir runner'ın başka bir projenin görevini
 * (dolayısıyla yanlış bir git deposunu) almaması burada garanti ediliyor.
 */
class RunnerConnectionTest {

    private final UUID organizationId = UUID.randomUUID();
    private final UUID ownerUserId = UUID.randomUUID();

    @Test
    void yeni_kayit_offline_baslar() {
        RunnerConnection runner = RunnerConnection.register(organizationId, ownerUserId, null, "ev");
        assertThat(runner.isOnline()).isFalse();
    }

    @Test
    void heartbeat_online_yapar_ve_zaman_damgasi_atar() {
        RunnerConnection runner = RunnerConnection.register(organizationId, ownerUserId, null, "ev");

        runner.recordHeartbeat();

        assertThat(runner.isOnline()).isTrue();
        assertThat(runner.getLastHeartbeatAt()).isNotNull();
    }

    @Test
    void markOffline_online_bir_runneri_offline_yapar() {
        RunnerConnection runner = RunnerConnection.register(organizationId, ownerUserId, null, "ev");
        runner.recordHeartbeat();

        runner.markOffline();

        assertThat(runner.isOnline()).isFalse();
    }

    @Test
    void projeye_kilitli_olmayan_runner_her_projeye_acik() {
        RunnerConnection runner = RunnerConnection.register(organizationId, ownerUserId, null, "ev");

        assertThat(runner.servesProject(UUID.randomUUID())).isTrue();
        assertThat(runner.servesProject(UUID.randomUUID())).isTrue();
    }

    @Test
    void projeye_kilitli_runner_yalnizca_o_projeye_acik() {
        UUID lockedProjectId = UUID.randomUUID();
        RunnerConnection runner = RunnerConnection.register(organizationId, ownerUserId, lockedProjectId, "iş");

        assertThat(runner.servesProject(lockedProjectId)).isTrue();
        assertThat(runner.servesProject(UUID.randomUUID())).isFalse();
    }

    @Test
    void bridge_token_ihrac_edilip_geri_alinabilir() {
        RunnerConnection runner = RunnerConnection.register(organizationId, ownerUserId, null, "ev");

        runner.issueBridgeToken("hash123");
        assertThat(runner.getBridgeTokenHash()).isEqualTo("hash123");
        assertThat(runner.getBridgeTokenIssuedAt()).isNotNull();

        runner.revokeBridgeToken();
        assertThat(runner.getBridgeTokenHash()).isNull();
        assertThat(runner.getBridgeTokenIssuedAt()).isNull();
    }
}
