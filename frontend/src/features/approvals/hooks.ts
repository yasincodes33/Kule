import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { approvalApi } from './api';

export function useApprovalsQuery() {
  return useQuery({
    queryKey: ['approvals'],
    queryFn: () => approvalApi.listPending(),
    select: (page) => page.content,
    refetchInterval: 8000,
  });
}

export function useDecideApprovalMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ taskId, approvalId, decision, reason }: { taskId: string; approvalId: string; decision: 'approve' | 'reject'; reason: string }) =>
      decision === 'approve' ? approvalApi.approve(taskId, approvalId) : approvalApi.reject(taskId, approvalId, reason),
    onSuccess: (_data, vars) => {
      queryClient.invalidateQueries({ queryKey: ['approvals'] });
      queryClient.invalidateQueries({ queryKey: ['task', vars.taskId] });
    },
  });
}
