import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { TaskType } from '../../shared/domain/enums';
import { runnerApi } from './api';

export function useRunnersQuery() {
  return useQuery({
    queryKey: ['runners'],
    queryFn: () => runnerApi.list(),
    select: (page) => page.content,
    refetchInterval: 10000,
  });
}

export function useRegisterRunnerMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ label, capabilities }: { label: string; capabilities: TaskType[] }) => runnerApi.register(label, capabilities),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['runners'] }),
  });
}

export function useIssueBridgeTokenMutation() {
  return useMutation({
    mutationFn: (runnerId: string) => runnerApi.issueBridgeToken(runnerId),
  });
}

export function useAddRunnerCapabilityMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ runnerId, taskType }: { runnerId: string; taskType: TaskType }) => runnerApi.addCapability(runnerId, taskType),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['runners'] }),
  });
}

export function useRemoveRunnerMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (runnerId: string) => runnerApi.remove(runnerId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['runners'] }),
  });
}
