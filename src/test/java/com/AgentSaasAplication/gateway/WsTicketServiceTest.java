package com.AgentSaasAplication.gateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * WsTicketService, WebSocket handshake'inde asıl access token yerine kullanılan tek
 * kullanımlık biletleri üretir. Bu testler issue/consume roundtrip'ini ve "bir kez
 * tüketilince bir daha geçerli olmama" garantisini doğrular.
 *
 * consume() atomik get-and-delete'i `redisTemplate.execute(RedisCallback)` üzerinden bir
 * Lua script (EVAL) ile yapar; bu yüzden burada RedisConnection'ın kendisi değil,
 * `execute(...)`'un döndürdüğü değer mock'lanır.
 */
@ExtendWith(MockitoExtension.class)
class WsTicketServiceTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    private WsTicketService service;

    @BeforeEach
    void setUp() {
        service = new WsTicketService(redisTemplate);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    private void stubExecuteResult(String value) {
        when(redisTemplate.execute(any(RedisCallback.class))).thenReturn(value);
    }

    @Test
    void issueTicket_redise_userId_organizationId_taskId_i_60sn_ttl_ile_yazar() {
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        String ticket = service.issueTicket(userId, organizationId, taskId);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(valueOperations).set(keyCaptor.capture(), valueCaptor.capture(), eq(Duration.ofSeconds(60)));
        assertThat(keyCaptor.getValue()).isEqualTo("ws-ticket:" + ticket);
        assertThat(valueCaptor.getValue()).isEqualTo(userId + "|" + organizationId + "|" + taskId);
    }

    @Test
    void consume_gecerli_bir_bileti_cozer() {
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        stubExecuteResult(userId + "|" + organizationId + "|" + taskId);

        Optional<WsTicketService.TaskLogTicket> result = service.consume("t1");

        assertThat(result).isPresent();
        assertThat(result.get().userId()).isEqualTo(userId);
        assertThat(result.get().organizationId()).isEqualTo(organizationId);
        assertThat(result.get().taskId()).isEqualTo(taskId);
    }

    @Test
    void consume_redis_de_yoksa_bos_doner() {
        stubExecuteResult(null);

        assertThat(service.consume("olmayan-bilet")).isEmpty();
    }

    @Test
    void consume_bozuk_bir_deger_icin_bos_doner() {
        stubExecuteResult("bozuk-deger");

        assertThat(service.consume("t2")).isEmpty();
    }

    @Test
    void ayni_bilet_iki_kez_tuketilemez() {
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        // Lua script atomik olduğu için gerçek Redis'te ikinci çağrı null döner — burada
        // execute()'un ikinci invocation'ında null dönmesini simüle ediyoruz.
        when(redisTemplate.execute(any(RedisCallback.class)))
                .thenReturn(userId + "|" + organizationId + "|" + taskId)
                .thenReturn(null);

        assertThat(service.consume("t3")).isPresent();
        assertThat(service.consume("t3")).isEmpty();
    }

    /**
     * Terminal biletine taskId eklendi — yalnızca görev sayfasından "bu araçla başlat"
     * ile açılan oturumlarda dolu, RunnersPage'deki genel amaçlı erişimde null. İkisinin de
     * round-trip'te doğru çözüldüğünü kanıtlıyor.
     */
    @Test
    void issueTerminalTicket_taskId_verildiginde_deger_dogru_kodlanir() {
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID runnerConnectionId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        service.issueTerminalTicket(userId, organizationId, runnerConnectionId, taskId);

        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(valueOperations).set(any(), valueCaptor.capture(), eq(Duration.ofSeconds(60)));
        assertThat(valueCaptor.getValue()).isEqualTo(userId + "|" + organizationId + "|" + runnerConnectionId + "|" + taskId);
    }

    @Test
    void consumeTerminalTicket_taskId_ile_dogru_cozer() {
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID runnerConnectionId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        stubExecuteResult(userId + "|" + organizationId + "|" + runnerConnectionId + "|" + taskId);

        Optional<WsTicketService.TerminalTicket> result = service.consumeTerminalTicket("t4");

        assertThat(result).isPresent();
        assertThat(result.get().taskId()).isEqualTo(taskId);
    }

    @Test
    void consumeTerminalTicket_taskId_yokken_null_doner() {
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID runnerConnectionId = UUID.randomUUID();
        stubExecuteResult(userId + "|" + organizationId + "|" + runnerConnectionId + "|null");

        Optional<WsTicketService.TerminalTicket> result = service.consumeTerminalTicket("t5");

        assertThat(result).isPresent();
        assertThat(result.get().taskId()).isNull();
        assertThat(result.get().runnerConnectionId()).isEqualTo(runnerConnectionId);
    }
}
