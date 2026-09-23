import { fireEvent, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { renderWithProviders } from '../../test/renderWithProviders';
import { useAuthStore } from '../../shared/auth/authStore';
import type { NotificationResponse, Page } from '../../shared/api/types';
import { notificationApi } from './api';
import NotificationsPage from './NotificationsPage';

vi.mock('./api', () => ({
  notificationApi: { list: vi.fn(), unreadCount: vi.fn(), markRead: vi.fn() },
}));

/** Bildirim sayfası: listeleme, okundu işaretleme ve boş durum. */
describe('NotificationsPage', () => {
  const notification: NotificationResponse = {
    id: 'notif-1',
    type: 'TASK_COMPLETED',
    payload: { taskId: 'task-1', title: 'prod deploy' },
    read: false,
    createdAt: new Date().toISOString(),
  };
  const page: Page<NotificationResponse> = {
    content: [notification],
    totalElements: 1,
    totalPages: 1,
    number: 0,
    size: 50,
    first: true,
    last: true,
  };

  beforeEach(() => {
    vi.clearAllMocks();
    useAuthStore.setState({ activeOrgId: 'org-1' });
  });

  it('hic bildirim yoksa temiz durumu gosterir', async () => {
    vi.mocked(notificationApi.list).mockResolvedValue({ ...page, content: [], totalElements: 0 });

    renderWithProviders(<NotificationsPage />);

    expect(await screen.findByText('Hiç bildirim yok')).toBeInTheDocument();
  });

  it('okunmamis bildirim icin OKUNDU ISARETLE gosterir ve tiklaninca markRead cagirir', async () => {
    vi.mocked(notificationApi.list).mockResolvedValue(page);
    vi.mocked(notificationApi.markRead).mockResolvedValue({ ...notification, read: true });

    renderWithProviders(<NotificationsPage />);
    const markButton = await screen.findByText('OKUNDU İŞARETLE');

    fireEvent.click(markButton);

    await waitFor(() => expect(notificationApi.markRead).toHaveBeenCalledWith('notif-1'));
  });

  it('okunmus bildirim icin OKUNDU ISARETLE butonu gosterilmez', async () => {
    vi.mocked(notificationApi.list).mockResolvedValue({ ...page, content: [{ ...notification, read: true }] });

    renderWithProviders(<NotificationsPage />);
    await waitFor(() => expect(notificationApi.list).toHaveBeenCalled());

    expect(screen.queryByText('OKUNDU İŞARETLE')).not.toBeInTheDocument();
  });

  it('activeOrgId yoksa sorgu hic calismaz', () => {
    useAuthStore.setState({ activeOrgId: null });

    renderWithProviders(<NotificationsPage />);

    expect(notificationApi.list).not.toHaveBeenCalled();
  });
});
