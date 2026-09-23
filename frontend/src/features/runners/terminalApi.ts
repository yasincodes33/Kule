import { api } from '../../shared/api/client';
import type { Page, RunnerTerminalSessionResponse, WsTicketResponse } from '../../shared/api/types';

export const runnerTerminalApi = {
  listPending: () => api.get<Page<RunnerTerminalSessionResponse>>('/terminal-sessions?size=100'),
  requestSession: (runnerId: string) =>
    api.post<RunnerTerminalSessionResponse>(`/runners/${runnerId}/terminal-sessions`),
  getSession: (requestId: string) => api.get<RunnerTerminalSessionResponse>(`/terminal-sessions/${requestId}`),
  approve: (requestId: string) => api.post<RunnerTerminalSessionResponse>(`/terminal-sessions/${requestId}/approve`),
  reject: (requestId: string) => api.post<RunnerTerminalSessionResponse>(`/terminal-sessions/${requestId}/reject`),
  // [Runner terminal] Asıl access token WS URL'sine hiç taşınmıyor — bkz. useTaskLogStream.ts'teki
  // aynı gerekçe. Yalnızca APPROVED + isteği açan kullanıcıya ait bir istek için üretilir.
  // `taskId` opsiyonel — yalnızca görev sayfasından "bu araçla başlat" ile çağrıldığında verilir,
  // terminal çıktısının o görevin kalıcı log akışına yazılabilmesi için (bkz. backend Faz B).
  issueWsTicket: (runnerId: string, requestId: string, taskId?: string) =>
    api.post<WsTicketResponse>(`/runners/${runnerId}/terminal-sessions/${requestId}/ws-ticket`, taskId ? { taskId } : undefined),
};
