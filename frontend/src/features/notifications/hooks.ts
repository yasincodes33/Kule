import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useAuthStore } from '../../shared/auth/authStore';
import { notificationApi } from './api';

export function useNotificationsQuery() {
  const activeOrgId = useAuthStore((s) => s.activeOrgId);
  return useQuery({
    queryKey: ['notifications', activeOrgId],
    queryFn: () => notificationApi.list(),
    select: (page) => page.content,
    enabled: !!activeOrgId,
    refetchInterval: 15000,
  });
}

export function useUnreadCountQuery() {
  const activeOrgId = useAuthStore((s) => s.activeOrgId);
  return useQuery({
    queryKey: ['notifications', 'unread-count', activeOrgId],
    queryFn: () => notificationApi.unreadCount(),
    select: (res) => res.count,
    enabled: !!activeOrgId,
    refetchInterval: 15000,
  });
}

export function useMarkNotificationReadMutation() {
  const activeOrgId = useAuthStore((s) => s.activeOrgId);
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (notificationId: string) => notificationApi.markRead(notificationId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notifications', activeOrgId] });
      queryClient.invalidateQueries({ queryKey: ['notifications', 'unread-count', activeOrgId] });
    },
  });
}
