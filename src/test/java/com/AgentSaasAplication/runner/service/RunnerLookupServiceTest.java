package com.AgentSaasAplication.runner.service;

import com.AgentSaasAplication.common.tenant.TenantContext;
import com.AgentSaasAplication.runner.domain.RunnerConnection;
import com.AgentSaasAplication.runner.domain.RunnerStatus;
import com.AgentSaasAplication.runner.repository.RunnerConnectionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * findEligibleRunners()'ın sahiplik ve proje filtreleri — bulut connector'larının
 * araç çağrılarını YANLIŞ kullanıcının makinesine ya da YANLIŞ projenin diskine yönlendirmemesi
 * için tek savunma hattı.
 */
@ExtendWith(MockitoExtension.class)
class RunnerLookupServiceTest {

    @Mock
    private RunnerConnectionRepository runnerConnectionRepository;

    private RunnerLookupService service;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID ownerA = UUID.randomUUID();
    private final UUID ownerB = UUID.randomUUID();
    private final UUID projectX = UUID.randomUUID();
    private final UUID projectY = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new RunnerLookupService(runnerConnectionRepository);
        TenantContext.set(organizationId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void assignedUserId_verilirse_yalnizca_o_kullanicinin_runnerlari_donuyor() {
        RunnerConnection ownedByA = RunnerConnection.register(organizationId, ownerA, null, "a-makinesi");
        RunnerConnection ownedByB = RunnerConnection.register(organizationId, ownerB, null, "b-makinesi");
        when(runnerConnectionRepository.findByTenantIdAndStatus(organizationId, RunnerStatus.ONLINE))
                .thenReturn(List.of(ownedByA, ownedByB));

        List<RunnerConnection> eligible = service.findEligibleRunners(ownerA, null);

        assertThat(eligible).containsExactly(ownedByA);
    }

    @Test
    void assignedUserId_null_ise_sahiplik_filtresi_uygulanmaz() {
        RunnerConnection ownedByA = RunnerConnection.register(organizationId, ownerA, null, "a-makinesi");
        RunnerConnection ownedByB = RunnerConnection.register(organizationId, ownerB, null, "b-makinesi");
        when(runnerConnectionRepository.findByTenantIdAndStatus(organizationId, RunnerStatus.ONLINE))
                .thenReturn(List.of(ownedByA, ownedByB));

        List<RunnerConnection> eligible = service.findEligibleRunners(null, null);

        assertThat(eligible).containsExactlyInAnyOrder(ownedByA, ownedByB);
    }

    @Test
    void projectId_verilirse_baska_projeye_kilitli_runnerlar_elenir() {
        RunnerConnection lockedToX = RunnerConnection.register(organizationId, ownerA, projectX, "x-makinesi");
        RunnerConnection lockedToY = RunnerConnection.register(organizationId, ownerA, projectY, "y-makinesi");
        RunnerConnection openToAll = RunnerConnection.register(organizationId, ownerA, null, "genel-makine");
        when(runnerConnectionRepository.findByTenantIdAndStatus(organizationId, RunnerStatus.ONLINE))
                .thenReturn(List.of(lockedToX, lockedToY, openToAll));

        List<RunnerConnection> eligible = service.findEligibleRunners(null, projectX);

        assertThat(eligible).containsExactlyInAnyOrder(lockedToX, openToAll);
    }

    @Test
    void hicbir_online_runner_yoksa_bos_liste_doner() {
        when(runnerConnectionRepository.findByTenantIdAndStatus(organizationId, RunnerStatus.ONLINE))
                .thenReturn(List.of());

        assertThat(service.findEligibleRunners(ownerA, projectX)).isEmpty();
    }

    @Test
    void findOnlineRunnerId_uygun_runner_varsa_ilkinin_id_sini_doner() {
        // RunnerConnection.register(...) hiç save edilmediği için getId() normalde null döner
        // (@GeneratedValue) — gerçek kullanımda repository'den DOLU bir id ile geliyor, bu testte
        // de aynı durumu simüle etmek için id reflection'la set ediliyor.
        RunnerConnection runner = RunnerConnection.register(organizationId, ownerA, null, "a-makinesi");
        UUID runnerId = UUID.randomUUID();
        ReflectionTestUtils.setField(runner, "id", runnerId);
        when(runnerConnectionRepository.findByTenantIdAndStatus(organizationId, RunnerStatus.ONLINE))
                .thenReturn(List.of(runner));

        assertThat(service.findOnlineRunnerId(ownerA, null)).isEqualTo(runnerId);
    }

    @Test
    void findOnlineRunnerId_uygun_runner_yoksa_null_doner() {
        when(runnerConnectionRepository.findByTenantIdAndStatus(organizationId, RunnerStatus.ONLINE))
                .thenReturn(List.of());

        assertThat(service.findOnlineRunnerId(ownerA, null)).isNull();
    }
}
