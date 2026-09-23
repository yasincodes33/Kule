import { fireEvent, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { renderWithProviders } from '../../test/renderWithProviders';
import { useAuthStore } from '../../shared/auth/authStore';
import type { OrganizationResponse, PendingInvitationResponse } from '../../shared/api/types';
import { invitationApi, orgApi } from './api';
import { InvitationsPanel } from './InvitationsPanel';

vi.mock('./api', () => ({
  invitationApi: { listMine: vi.fn(), accept: vi.fn(), reject: vi.fn() },
  orgApi: { me: vi.fn(), listMine: vi.fn(), create: vi.fn() },
}));

/**
 * Davet kabul/red akışı: kabul hem organizations cache'ini günceller hem de
 * (onboarding sırasında) `onAccepted` callback'ini tetikler; red ise doğru membershipId
 * ile çağrılır.
 */
describe('InvitationsPanel', () => {
  const invitation: PendingInvitationResponse = {
    membershipId: 'mem-1',
    organizationId: 'org-1',
    organizationName: 'Acme Corp',
    role: 'DEVELOPER',
  };
  const orgs: OrganizationResponse[] = [{ id: 'org-1', name: 'Acme Corp', slug: 'acme-corp' }];

  beforeEach(() => {
    vi.clearAllMocks();
    useAuthStore.setState({ accessToken: 'token', activeOrgId: null, organizations: [] });
  });

  it('bekleyen davet yoksa hicbir sey render etmez', async () => {
    vi.mocked(invitationApi.listMine).mockResolvedValue([]);

    renderWithProviders(<InvitationsPanel />);

    await waitFor(() => expect(invitationApi.listMine).toHaveBeenCalled());
    expect(screen.queryByText('Bekleyen davetleriniz')).not.toBeInTheDocument();
  });

  it('bekleyen daveti organizasyon adiyla gosterir', async () => {
    vi.mocked(invitationApi.listMine).mockResolvedValue([invitation]);

    renderWithProviders(<InvitationsPanel />);

    expect(await screen.findByText('Acme Corp')).toBeInTheDocument();
    expect(screen.getByText('DEVELOPER')).toBeInTheDocument();
  });

  it('kabul et dogru membershipId ile accept cagirir, organizasyonlari yeniler ve onAccepted tetiklenir', async () => {
    vi.mocked(invitationApi.listMine).mockResolvedValue([invitation]);
    vi.mocked(invitationApi.accept).mockResolvedValue({
      id: 'mem-1', organizationId: 'org-1', userId: 'user-1', invitedEmail: null, role: 'DEVELOPER', status: 'ACTIVE',
    });
    vi.mocked(orgApi.listMine).mockResolvedValue(orgs);
    const onAccepted = vi.fn();

    renderWithProviders(<InvitationsPanel onAccepted={onAccepted} />);
    await screen.findByText('Acme Corp');

    fireEvent.click(screen.getByText('KABUL ET'));

    await waitFor(() => expect(invitationApi.accept).toHaveBeenCalledWith('mem-1'));
    await waitFor(() => expect(onAccepted).toHaveBeenCalled());
    // onboarding akışında activeOrgId boşken kabul edilen org otomatik seçilmeli — aksi halde
    // RequireAuth activeOrgId bekleyip kullanıcıyı boş bir ekranda bırakır (bkz. hooks.ts).
    await waitFor(() => expect(useAuthStore.getState().activeOrgId).toBe('org-1'));
  });

  it('reddet dogru membershipId ile reject cagirir', async () => {
    vi.mocked(invitationApi.listMine).mockResolvedValue([invitation]);
    vi.mocked(invitationApi.reject).mockResolvedValue(undefined);

    renderWithProviders(<InvitationsPanel />);
    await screen.findByText('Acme Corp');

    fireEvent.click(screen.getByText('REDDET'));

    await waitFor(() => expect(invitationApi.reject).toHaveBeenCalledWith('mem-1'));
  });
});
