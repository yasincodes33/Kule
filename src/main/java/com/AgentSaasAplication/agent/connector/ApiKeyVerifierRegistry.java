package com.AgentSaasAplication.agent.connector;

import com.AgentSaasAplication.agent.domain.AgentType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ApiKeyVerifierRegistry {

    private final Map<AgentType, ApiKeyVerifier> verifiers;

    /** ApiKeyVerifier uygulayan bean'ler aynı zamanda AgentConnector'ı da uyguluyor
     *  (AbstractApiAgentConnector bunu garanti ediyor) — getAgentType() buradan geliyor. */
    public ApiKeyVerifierRegistry(List<ApiKeyVerifier> verifierBeans) {
        this.verifiers = verifierBeans.stream()
                .collect(Collectors.toMap(v -> ((AgentConnector) v).getAgentType(), Function.identity()));
    }

    public Optional<ApiKeyVerifier> find(AgentType agentType) {
        return Optional.ofNullable(verifiers.get(agentType));
    }
}