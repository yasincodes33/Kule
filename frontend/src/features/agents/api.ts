import { api } from '../../shared/api/client';
import type { AgentConnectionResponse, Page } from '../../shared/api/types';
import type { AgentType, TaskType } from '../../shared/domain/enums';

export const agentApi = {
  list: () => api.get<Page<AgentConnectionResponse>>('/agents?size=100'),
  register: (agentType: AgentType, capabilities: TaskType[], apiKey: string) =>
    api.post<AgentConnectionResponse>('/agents', { agentType, capabilities, apiKey }),
  addCapability: (connectionId: string, taskType: TaskType) => api.post<void>(`/agents/${connectionId}/capabilities`, { taskType }),
  remove: (connectionId: string) => api.del<void>(`/agents/${connectionId}`),
};
