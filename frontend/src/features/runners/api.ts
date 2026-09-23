import { api } from '../../shared/api/client';
import type { Page, RunnerConnectionResponse, RunnerTokenResponse } from '../../shared/api/types';
import type { TaskType } from '../../shared/domain/enums';

export const runnerApi = {
  list: () => api.get<Page<RunnerConnectionResponse>>('/runners?size=100'),
  register: (label: string, capabilities: TaskType[]) =>
    api.post<RunnerConnectionResponse>('/runners', { ownerUserId: null, projectId: null, label, capabilities }),
  issueBridgeToken: (runnerId: string) => api.post<RunnerTokenResponse>(`/runners/${runnerId}/bridge-token`),
  addCapability: (runnerId: string, taskType: TaskType) => api.post<void>(`/runners/${runnerId}/capabilities`, { taskType }),
  remove: (runnerId: string) => api.del<void>(`/runners/${runnerId}`),
};
