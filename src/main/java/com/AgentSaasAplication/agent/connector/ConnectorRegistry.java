package com.AgentSaasAplication.agent.connector;

import com.AgentSaasAplication.agent.domain.AgentType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ConnectorRegistry {

    private final Map<AgentType, AgentConnector> connectors;

    public ConnectorRegistry(List<AgentConnector> connectorBeans) {
        this.connectors = connectorBeans.stream()
                .collect(Collectors.toMap(AgentConnector::getAgentType, Function.identity()));
    }

    public AgentConnector get(AgentType agentType) {
        AgentConnector connector = connectors.get(agentType);
        if (connector == null) {
            throw new IllegalStateException("Kayıtlı connector bulunamadı: " + agentType);
        }
        return connector;
    }
}