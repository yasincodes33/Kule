import { api } from '../../shared/api/client';
import type { Page, TaskLogResponse, TaskResponse } from '../../shared/api/types';
import type { ModelTier, TaskStatus, TaskType } from '../../shared/domain/enums';

export interface CreateTaskInput {
  type: TaskType;
  title: string;
  agentConnectionId?: string | null;
  runnerConnectionId?: string | null;
  preferredModelTier?: ModelTier | null;
  assignedUserId?: string | null;
  prompt?: string | null;
}

export const taskApi = {
  listForProject: (projectId: string, status?: TaskStatus) =>
    api.get<Page<TaskResponse>>(
      `/projects/${projectId}/tasks?size=200` + (status ? `&status=${status}` : ''),
    ),
  get: (taskId: string) => api.get<TaskResponse>(`/tasks/${taskId}`),
  create: (projectId: string, input: CreateTaskInput) =>
    api.post<TaskResponse>(`/projects/${projectId}/tasks`, input),
  retry: (taskId: string) => api.post<TaskResponse>(`/tasks/${taskId}/retry`),
  cancel: (taskId: string) => api.post<TaskResponse>(`/tasks/${taskId}/cancel`),
  // Modele/araca gönderilecek prompt'u sonradan düzenlemek için — bkz. TaskOrchestrationService.updatePrompt.
  updatePrompt: (taskId: string, prompt: string) => api.put<TaskResponse>(`/tasks/${taskId}/prompt`, { prompt }),
  logs: (taskId: string) => api.get<TaskLogResponse[]>(`/tasks/${taskId}/logs`),
  // Canlı log WebSocket'i artık asıl access token'ı değil, bu
  // uçtan alınan tek kullanımlık kısa ömürlü bir bilet taşıyor — bkz. useTaskLogStream.ts.
  issueLogsWsTicket: (taskId: string) => api.post<{ ticket: string }>(`/tasks/${taskId}/logs/ws-ticket`),
};
