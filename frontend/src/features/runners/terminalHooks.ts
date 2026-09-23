import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { runnerTerminalApi } from './terminalApi';

export function usePendingTerminalSessionsQuery() {
  return useQuery({
    queryKey: ['terminal-sessions', 'pending'],
    queryFn: () => runnerTerminalApi.listPending(),
    select: (page) => page.content,
    refetchInterval: 8000,
  });
}

export function useRequestTerminalSessionMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (runnerId: string) => runnerTerminalApi.requestSession(runnerId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['terminal-sessions'] }),
  });
}

/** İstek PENDING kaldığı sürece kısa aralıklarla yoklar — onaylayıcı kararını verince rozet
 * otomatik APPROVED/REJECTED'a döner (bkz. RunnersPage'deki "Bağlan" akışı). */
export function useTerminalSessionQuery(requestId: string | null) {
  return useQuery({
    queryKey: ['terminal-session', requestId],
    queryFn: () => runnerTerminalApi.getSession(requestId as string),
    enabled: !!requestId,
    refetchInterval: (query) => (query.state.data?.status === 'PENDING' ? 3000 : false),
  });
}

export function useDecideTerminalSessionMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ requestId, decision }: { requestId: string; decision: 'approve' | 'reject' }) =>
      decision === 'approve' ? runnerTerminalApi.approve(requestId) : runnerTerminalApi.reject(requestId),
    onSuccess: (_data, vars) => {
      queryClient.invalidateQueries({ queryKey: ['terminal-sessions'] });
      queryClient.invalidateQueries({ queryKey: ['terminal-session', vars.requestId] });
    },
  });
}
