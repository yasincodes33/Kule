package com.AgentSaasAplication.task.service;

import com.AgentSaasAplication.common.cluster.SchedulerLock;
import com.AgentSaasAplication.identity.service.OrganizationSweepService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Kilit alınamazsa (başka bir instance zaten bu tick'i yürütüyor)
 * sweep'in HİÇ çalışmadığını, kilit alınırsa normal şekilde çalıştığını doğruluyor — dört
 * scheduler aynı deseni paylaştığı için burada yalnızca biri (temsilen) test ediliyor.
 */
@ExtendWith(MockitoExtension.class)
class StaleDispatchSchedulerTest {

    @Mock private OrganizationSweepService organizationSweepService;
    @Mock private StaleDispatchService staleDispatchService;
    @Mock private SchedulerLock schedulerLock;

    @Test
    void kilit_alinamazsa_hicbir_organizasyon_taranmaz() {
        when(schedulerLock.tryAcquire(eq("stale-dispatch-sweep"), any(Duration.class))).thenReturn(false);
        StaleDispatchScheduler scheduler = new StaleDispatchScheduler(organizationSweepService, staleDispatchService, schedulerLock);

        scheduler.sweepStaleDispatches();

        verify(organizationSweepService, never()).allOrganizationIds();
    }

    @Test
    void kilit_alinirsa_organizasyonlar_normal_sekilde_taranir() {
        UUID orgId = UUID.randomUUID();
        when(schedulerLock.tryAcquire(eq("stale-dispatch-sweep"), any(Duration.class))).thenReturn(true);
        when(organizationSweepService.allOrganizationIds()).thenReturn(List.of(orgId));
        when(staleDispatchService.findStaleDispatchIds(any())).thenReturn(List.of());
        StaleDispatchScheduler scheduler = new StaleDispatchScheduler(organizationSweepService, staleDispatchService, schedulerLock);

        scheduler.sweepStaleDispatches();

        verify(organizationSweepService).allOrganizationIds();
    }
}
