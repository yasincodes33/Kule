import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { AgentType, TaskType } from '../../shared/domain/enums';
import { agentApi } from './api';

export function useAgentConnectionsQuery() {
  return useQuery({
    queryKey: ['agentConnections'],
    queryFn: () => agentApi.list(),
    select: (page) => page.content,
    refetchInterval: 10000,
  });
}

export function useRegisterAgentMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ agentType, capabilities, apiKey }: { agentType: AgentType; capabilities: TaskType[]; apiKey: string }) =>
      agentApi.register(agentType, capabilities, apiKey),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['agentConnections'] }),
  });
}

export function useAddAgentCapabilityMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ connectionId, taskType }: { connectionId: string; taskType: TaskType }) => agentApi.addCapability(connectionId, taskType),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['agentConnections'] }),
  });
}

export function useRemoveAgentMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (connectionId: string) => agentApi.remove(connectionId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['agentConnections'] }),
  });
}
