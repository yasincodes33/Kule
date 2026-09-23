import { api } from '../../shared/api/client';
import type { NotificationResponse, Page } from '../../shared/api/types';

export const notificationApi = {
  list: () => api.get<Page<NotificationResponse>>('/me/notifications?size=50'),
  unreadCount: () => api.get<{ count: number }>('/me/notifications/unread-count'),
  markRead: (notificationId: string) => api.post<NotificationResponse>(`/me/notifications/${notificationId}/read`),
};
