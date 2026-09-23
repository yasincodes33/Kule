package com.AgentSaasAplication.agent.service;

import com.AgentSaasAplication.agent.connector.AbstractApiAgentConnector;
import com.AgentSaasAplication.agent.connector.AgentConnector;
import com.AgentSaasAplication.agent.connector.ConnectorRegistry;
import com.AgentSaasAplication.agent.domain.AgentConnection;
import com.AgentSaasAplication.common.domain.ModelTier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * API anahtarıyla bağlanan bulut modellerini, bir görevi baştan sona çalıştırmak DIŞINDA
 * da kullanan yardımcı servis: görev triyajı, commit mesajı üretimi, kod incelemesi ve terminal
 * logu özeti.
 *
 * Bunların hepsi tek atışlık istemler — bu yüzden {@code executeTask}'in araçlı/çok turlu
 * döngüsünü değil, {@link AbstractApiAgentConnector#assist} yolunu kullanıyorlar.
 *
 * Tasarım kararı: hiçbiri ZORUNLU değil. Organizasyonda kayıtlı bir API ajanı yoksa ya da çağrı
 * hata verirse {@link Optional#empty()} döner ve çağıran taraf işine normal şekilde devam eder —
 * yardımcı bir özellik, ana akışı asla kırmamalı.
 */
@Slf4j
@Service
public class AiAssistService {

    /** Modele gönderilen bağlam üst sınırı — dev diff'ler/loglar isteği şişirmesin. */
    private static final int MAX_CONTEXT_CHARS = 60_000;

    private final AgentConnectionService agentConnectionService;
    private final ConnectorRegistry connectorRegistry;

    public AiAssistService(AgentConnectionService agentConnectionService, ConnectorRegistry connectorRegistry) {
        this.agentConnectionService = agentConnectionService;
        this.connectorRegistry = connectorRegistry;
    }

    /** Organizasyonda kullanılabilir bir API ajanı var mı? */
    public boolean isAvailable() {
        return pickConnection().isPresent();
    }

    /**
     * Tek atışlık bir istem çalıştırır.
     *
     * @param tier BUDGET sık/otomatik çağrılar için (triyaj, özet), DEFAULT kaliteye duyarlı
     *             olanlar için (kod incelemesi).
     */
    public Optional<String> ask(ModelTier tier, String systemPrompt, String userPrompt) {
        Optional<AgentConnection> connection = pickConnection();
        if (connection.isEmpty()) {
            return Optional.empty();
        }
        AgentConnector connector = connectorRegistry.get(connection.get().getAgentType());
        if (!(connector instanceof AbstractApiAgentConnector api)) {
            return Optional.empty();
        }
        try {
            String answer = api.assist(connection.get(), tier, systemPrompt, truncate(userPrompt));
            return answer == null || answer.isBlank() ? Optional.empty() : Optional.of(answer.trim());
        } catch (Exception e) {
            // Yardımcı özellik: başarısızlık ana akışı durdurmamalı, yalnızca loglanır.
            log.warn("AI yardımcı çağrısı başarısız: agentType={}, hata={}",
                    connection.get().getAgentType(), e.toString());
            return Optional.empty();
        }
    }

    private Optional<AgentConnection> pickConnection() {
        return agentConnectionService.listAgentConnections(
                        org.springframework.data.domain.PageRequest.of(0, 20))
                .getContent().stream()
                .filter(AgentConnection::isOnline)
                .findFirst();
    }

    /** Sondan değil BAŞTAN kırpıyoruz: diff/log'larda en yeni kısım sonda ve daha bilgilendirici. */
    private String truncate(String text) {
        if (text == null || text.length() <= MAX_CONTEXT_CHARS) {
            return text;
        }
        return "… (başı kırpıldı)\n" + text.substring(text.length() - MAX_CONTEXT_CHARS);
    }
}
