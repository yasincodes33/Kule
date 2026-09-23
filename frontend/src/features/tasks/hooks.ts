import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useAuthStore } from '../../shared/auth/authStore';
import { projectApi } from '../projects/api';
import { taskApi, type CreateTaskInput } from './api';

export function useProjectsQuery() {
  const activeOrgId = useAuthStore((s) => s.activeOrgId);
  return useQuery({
    queryKey: ['projects', activeOrgId],
    queryFn: () => projectApi.listForOrg(activeOrgId as string),
    enabled: !!activeOrgId,
    select: (page) => page.content,
  });
}

export function useTasksQuery(projectId: string | null) {
  return useQuery({
    queryKey: ['tasks', projectId],
    queryFn: () => taskApi.listForProject(projectId as string),
    enabled: !!projectId,
    select: (page) => page.content,
    refetchInterval: 8000,
  });
}

export function useCreateTaskMutation(projectId: string | null) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: CreateTaskInput) => taskApi.create(projectId as string, input),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['tasks', projectId] }),
  });
}

export function useCancelTaskMutation(projectId: string | null) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (taskId: string) => taskApi.cancel(taskId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['tasks', projectId] }),
  });
}

export function useRetryTaskMutation(projectId: string | null) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (taskId: string) => taskApi.retry(taskId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['tasks', projectId] }),
  });
}

export function useTaskQuery(taskId: string | null) {
  return useQuery({
    queryKey: ['task', taskId],
    queryFn: () => taskApi.get(taskId as string),
    enabled: !!taskId,
    refetchInterval: 5000,
  });
}

export function useTaskLogsQuery(taskId: string | null) {
  return useQuery({
    queryKey: ['taskLogs', taskId],
    queryFn: () => taskApi.logs(taskId as string),
    enabled: !!taskId,
    // `staleTime: Infinity` YANLIŞTI: log listesi görev sürerken büyüyor. Sayfadan çıkıp geri
    // dönüldüğünde react-query önbellekteki ESKİ anlık görüntüyü döndürüyor, canlı akışta
    // görülen satırlar ise bileşenle birlikte gittiği için ekran boş kalıyordu ("terminalde
    // olanlar canlı geliyor ama sonra bakınca yok" hatası). Her mount'ta yeniden çekiyoruz;
    // sayfa açıkken güncellik zaten WebSocket akışından geliyor, ekstra polling gerekmiyor.
    staleTime: 0,
    refetchOnMount: 'always',
  });
}

/** Görev detay sayfasından iptal/yeniden dağıt — hem tekil hem projeye ait listeyi tazeler. */
export function useTaskDetailActionMutation(taskId: string, projectId: string | null | undefined) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (action: 'cancel' | 'retry') => (action === 'cancel' ? taskApi.cancel(taskId) : taskApi.retry(taskId)),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['task', taskId] });
      if (projectId) queryClient.invalidateQueries({ queryKey: ['tasks', projectId] });
    },
  });
}

/** Modele/araca gönderilecek prompt'u düzenlemek için — bkz. TaskDetailPage'deki PromptPanel. */
export function useUpdateTaskPromptMutation(taskId: string, projectId: string | null | undefined) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (prompt: string) => taskApi.updatePrompt(taskId, prompt),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['task', taskId] });
      if (projectId) queryClient.invalidateQueries({ queryKey: ['tasks', projectId] });
    },
  });
}
