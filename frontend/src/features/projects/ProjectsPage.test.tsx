import { fireEvent, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { renderWithProviders } from '../../test/renderWithProviders';
import { useAuthStore } from '../../shared/auth/authStore';
import type { Page, ProjectResponse } from '../../shared/api/types';
import { projectApi } from './api';
import ProjectsPage from './ProjectsPage';

vi.mock('./api', () => ({
  projectApi: { listForOrg: vi.fn(), get: vi.fn(), create: vi.fn(), update: vi.fn() },
}));

/** Proje oluşturma ve düzenleme; aynı ProjectForm'un iki modda (create/edit) doğru
 * çalıştığı dahil. */
describe('ProjectsPage', () => {
  const project: ProjectResponse = {
    id: 'proj-1',
    name: 'billing-svc',
    repoUrl: 'https://github.com/org/billing-svc',
    defaultBranch: 'main',
  };
  const page: Page<ProjectResponse> = {
    content: [project],
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

  it('hic proje yoksa bos durumu gosterir', async () => {
    vi.mocked(projectApi.listForOrg).mockResolvedValue({ ...page, content: [], totalElements: 0 });

    renderWithProviders(<ProjectsPage />);

    expect(await screen.findByText('Bu organizasyonda henüz proje yok')).toBeInTheDocument();
  });

  it('projeleri tabloda listeler', async () => {
    vi.mocked(projectApi.listForOrg).mockResolvedValue(page);

    renderWithProviders(<ProjectsPage />);

    expect(await screen.findByText('billing-svc')).toBeInTheDocument();
    expect(screen.getByText('https://github.com/org/billing-svc')).toBeInTheDocument();
  });

  it('yeni proje formu dolduruldugunda organizasyon icinde create cagirir', async () => {
    vi.mocked(projectApi.listForOrg).mockResolvedValue({ ...page, content: [], totalElements: 0 });
    vi.mocked(projectApi.create).mockResolvedValue({ ...project, id: 'proj-new' });

    renderWithProviders(<ProjectsPage />);
    await screen.findByText('Bu organizasyonda henüz proje yok');

    fireEvent.click(screen.getByText('Yeni proje'));
    fireEvent.change(screen.getByPlaceholderText('billing-svc'), { target: { value: 'yeni-servis' } });
    fireEvent.change(screen.getByPlaceholderText('https://github.com/org/billing-svc'), {
      target: { value: 'https://github.com/org/yeni-servis' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Proje oluştur' }));

    await waitFor(() =>
      expect(projectApi.create).toHaveBeenCalledWith('org-1', 'yeni-servis', 'https://github.com/org/yeni-servis', 'main'),
    );
  });

  it('duzenle tiklaninca ayni form mevcut degerlerle doldurulmus duzenleme moduna gecer', async () => {
    vi.mocked(projectApi.listForOrg).mockResolvedValue(page);
    vi.mocked(projectApi.update).mockResolvedValue({ ...project, name: 'billing-svc-v2' });

    renderWithProviders(<ProjectsPage />);
    await screen.findByText('billing-svc');

    fireEvent.click(screen.getByText('DÜZENLE'));
    const nameInput = screen.getByDisplayValue('billing-svc');
    fireEvent.change(nameInput, { target: { value: 'billing-svc-v2' } });
    fireEvent.click(screen.getByText('Kaydet'));

    await waitFor(() =>
      expect(projectApi.update).toHaveBeenCalledWith('proj-1', 'billing-svc-v2', 'https://github.com/org/billing-svc', 'main'),
    );
  });

  it('proje adi veya repo url bossa olustur butonu devre disi kalir', async () => {
    vi.mocked(projectApi.listForOrg).mockResolvedValue({ ...page, content: [], totalElements: 0 });

    renderWithProviders(<ProjectsPage />);
    await screen.findByText('Bu organizasyonda henüz proje yok');
    fireEvent.click(screen.getByText('Yeni proje'));

    expect(screen.getByRole('button', { name: 'Proje oluştur' })).toBeDisabled();
    expect(projectApi.create).not.toHaveBeenCalled();
  });
});
