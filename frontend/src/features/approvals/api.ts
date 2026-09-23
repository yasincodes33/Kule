import { api } from '../../shared/api/client';
import type { ApprovalResponse, Page } from '../../shared/api/types';

export const approvalApi = {
  listPending: () => api.get<Page<ApprovalResponse>>('/approvals?size=100'),
  approve: (taskId: string, approvalId: string) =>
    api.post<ApprovalResponse>(`/tasks/${taskId}/approvals/${approvalId}/approve`),
  reject: (taskId: string, approvalId: string, reason: string) =>
    api.post<ApprovalResponse>(`/tasks/${taskId}/approvals/${approvalId}/reject`, reason ? { reason } : undefined),
};
