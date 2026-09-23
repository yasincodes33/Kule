import { fireEvent, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { renderWithProviders } from '../../test/renderWithProviders';
import type { Page, RunnerConnectionResponse } from '../../shared/api/types';
import { runnerApi } from './api';
import RunnersPage from './RunnersPage';

vi.mock('./api', () => ({
  runnerApi: { list: vi.fn(), register: vi.fn(), issueBridgeToken: vi.fn(), addCapability: vi.fn(), remove: vi.fn() },
}));
// RunnersPage varsayılan sekmede yalnızca RunnerFleet'i mount eder ama AgentsPanel'i de import
// eder — o da kendi api modülünü çağırdığı için mount edilmese bile modül düzeyinde import
// zinciri çözülüyor, bu yüzden gerçek fetch'e düşmesin diye mock'lanıyor.
vi.mock('../agents/api', () => ({
  agentApi: { list: vi.fn().mockResolvedValue({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 100, first: true, last: true }) },
}));

/**
 * Runner filosu yönetimi: kayıt, bridge token üretimi, yetenek ekleme ve kaldırma.
 */
describe('RunnersPage', () => {
  const runner: RunnerConnectionResponse = {
    id: 'runner-12345678',
    ownerUserId: 'user-1',
    projectId: null,
    label: 'atlas-03',
    status: 'ONLINE',
    lastHeartbeatAt: new Date().toISOString(),
    capabilities: ['DEV'],
  };
  const page: Page<RunnerConnectionResponse> = {
    content: [runner],
    totalElements: 1,
    totalPages: 1,
    number: 0,
    size: 100,
    first: true,
    last: true,
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('filo bossa bos durumu gosterir', async () => {
    vi.mocked(runnerApi.list).mockResolvedValue({ ...page, content: [], totalElements: 0 });

    renderWithProviders(<RunnersPage />);

    expect(await screen.findByText('Henüz bağlı runner yok')).toBeInTheDocument();
  });

  it('runner karti label ve durumu gosterir', async () => {
    vi.mocked(runnerApi.list).mockResolvedValue(page);

    renderWithProviders(<RunnersPage />);

    expect(await screen.findByText('atlas-03')).toBeInTheDocument();
    expect(screen.getByText('1/1 online')).toBeInTheDocument();
  });

  it('bridge token olustur tiklanince token uretilir ve tek seferlik gosterilir', async () => {
    vi.mocked(runnerApi.list).mockResolvedValue(page);
    vi.mocked(runnerApi.issueBridgeToken).mockResolvedValue({ token: 'gizli-token-abc' });

    renderWithProviders(<RunnersPage />);
    await screen.findByText('atlas-03');

    fireEvent.click(screen.getByText('BRIDGE TOKEN OLUŞTUR'));

    expect(await screen.findByText('gizli-token-abc')).toBeInTheDocument();
    expect(runnerApi.issueBridgeToken).toHaveBeenCalledWith('runner-12345678');
  });

  it('eksik bir yetenek rozetine tiklamak addCapability cagirir', async () => {
    vi.mocked(runnerApi.list).mockResolvedValue(page);
    vi.mocked(runnerApi.addCapability).mockResolvedValue(undefined);

    renderWithProviders(<RunnersPage />);
    await screen.findByText('atlas-03');

    fireEvent.click(screen.getByTitle('DEPLOY yeteneği ekle'));

    await waitFor(() => expect(runnerApi.addCapability).toHaveBeenCalledWith('runner-12345678', 'DEPLOY'));
  });

  it('kaldir tiklaninca remove cagirir', async () => {
    vi.mocked(runnerApi.list).mockResolvedValue(page);
    vi.mocked(runnerApi.remove).mockResolvedValue(undefined);

    renderWithProviders(<RunnersPage />);
    await screen.findByText('atlas-03');

    fireEvent.click(screen.getByText('KALDIR'));

    await waitFor(() => expect(runnerApi.remove).toHaveBeenCalledWith('runner-12345678'));
  });

  it('yeni runner kaydi formu dolduruldugunda register cagirir', async () => {
    vi.mocked(runnerApi.list).mockResolvedValue({ ...page, content: [], totalElements: 0 });
    vi.mocked(runnerApi.register).mockResolvedValue({ ...runner, id: 'runner-new' });

    renderWithProviders(<RunnersPage />);
    await screen.findByText('Henüz bağlı runner yok');

    fireEvent.click(screen.getByText('Bağlantı ekle'));
    fireEvent.change(screen.getByPlaceholderText('atlas-03'), { target: { value: 'ev-runner' } });
    fireEvent.click(screen.getByText('Kaydet'));

    await waitFor(() => expect(runnerApi.register).toHaveBeenCalledWith('ev-runner', ['DEV']));
  });
});
