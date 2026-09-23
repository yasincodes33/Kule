package com.AgentSaasAplication.agent.connector;

import com.AgentSaasAplication.agent.domain.AgentConnection;
import com.AgentSaasAplication.agent.domain.AgentType;
import com.AgentSaasAplication.common.domain.ModelTier;
import com.AgentSaasAplication.task.domain.Task;

public interface AgentConnector {
    AgentType getAgentType();

    boolean executeTask(AgentConnection connection, Task task, ModelTier preferredModelTier);

    boolean healthCheck(AgentConnection connection);
}