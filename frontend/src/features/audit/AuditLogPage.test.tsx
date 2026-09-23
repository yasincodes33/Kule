import { screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { renderWithProviders } from '../../test/renderWithProviders';
import { useAuthStore } from '../../shared/auth/authStore';
import type { AuditLogResponse, Page } from '../../shared/api/types';
import { auditApi } from './api';
import AuditLogPage from './AuditLogPage';

vi.mock('./api', () => ({
  auditApi: { list: vi.fn() },
}));

/** Denetim kaydı sayfası: listeleme, aktör gösterimi ve boş durum. */
describe('AuditLogPage', () => {
  const entry: AuditLogResponse = {
    id: 'audit-1',
    actorUserId: 'user-12345678',
    action: 'MEMBER_INVITED',
    entityType: 'Membership',
    entityId: 'mem-12345678',
    metadata: null,
    createdAt: new Date().toISOString(),
  };
  const page: Page<AuditLogResponse> = {
    content: [entry],
    totalElements: 1,
    totalPages: 1,
    number: 0,
    size: 100,
    first: true,
    last: true,
  };

  beforeEach(() => {
    vi.clearAllMocks();
    useAuthStore.setState({ activeOrgId: 'org-1' });
  });

  it('henuz kayit yoksa temiz durumu gosterir', async () => {
    vi.mocked(auditApi.list).mockResolvedValue({ ...page, content: [], totalElements: 0 });

    renderWithProviders(<AuditLogPage />);

    expect(await screen.findByText('Henüz denetim kaydı yok')).toBeInTheDocument();
  });

  it('olayi alt cizgiler bosluga cevrilmis sekilde ve aktor kisaltmasiyla listeler', async () => {
    vi.mocked(auditApi.list).mockResolvedValue(page);

    renderWithProviders(<AuditLogPage />);

    expect(await screen.findByText('MEMBER INVITED')).toBeInTheDocument();
    expect(screen.getByText('user-123')).toBeInTheDocument();
  });

  it('aktoru olmayan (sistem kaynakli) kayitlar icin "sistem" gosterir', async () => {
    vi.mocked(auditApi.list).mockResolvedValue({ ...page, content: [{ ...entry, actorUserId: null }] });

    renderWithProviders(<AuditLogPage />);

    expect(await screen.findByText('sistem')).toBeInTheDocument();
  });

  it('activeOrgId yoksa sorgu hic calismaz', () => {
    useAuthStore.setState({ activeOrgId: null });

    renderWithProviders(<AuditLogPage />);

    expect(auditApi.list).not.toHaveBeenCalled();
  });
});
