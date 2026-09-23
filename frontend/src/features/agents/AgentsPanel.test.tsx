import { fireEvent, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { renderWithProviders } from '../../test/renderWithProviders';
import type { AgentConnectionResponse, Page } from '../../shared/api/types';
import { agentApi } from './api';
import { AgentsPanel } from './AgentsPanel';

vi.mock('./api', () => ({
  agentApi: { list: vi.fn(), register: vi.fn(), addCapability: vi.fn(), remove: vi.fn() },
}));

/**
 * Bulut ajanı (Claude/ChatGPT/Gemini) API anahtarı bağlama, yetenek ekleme ve kaldırma
 * akışlarını kapsar.
 */
describe('AgentsPanel', () => {
  const agent: AgentConnectionResponse = {
    id: 'agent-12345678',
    agentType: 'CLAUDE',
    status: 'ONLINE',
    capabilities: ['DEV'],
  };
  const page: Page<AgentConnectionResponse> = {
    content: [agent],
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

  it('bagli ajan yoksa bos durumu gosterir', async () => {
    vi.mocked(agentApi.list).mockResolvedValue({ ...page, content: [], totalElements: 0 });

    renderWithProviders(<AgentsPanel />);

    expect(await screen.findByText('Henüz bağlı ajan yok')).toBeInTheDocument();
  });

  it('ajan kartini tipi ve durumuyla gosterir', async () => {
    vi.mocked(agentApi.list).mockResolvedValue(page);

    renderWithProviders(<AgentsPanel />);

    expect(await screen.findByText('ONLINE')).toBeInTheDocument();
    expect(screen.getByText('1/1 online')).toBeInTheDocument();
  });

  it('ajan baglama formu dolduruldugunda register cagirir', async () => {
    vi.mocked(agentApi.list).mockResolvedValue({ ...page, content: [], totalElements: 0 });
    vi.mocked(agentApi.register).mockResolvedValue({ ...agent, id: 'agent-new' });

    renderWithProviders(<AgentsPanel />);
    await screen.findByText('Henüz bağlı ajan yok');

    fireEvent.click(screen.getByText('Ajan bağla'));
    fireEvent.change(screen.getByPlaceholderText('sk-••••••••'), { target: { value: 'sk-test-key' } });
    fireEvent.click(screen.getByText('Kaydet'));

    await waitFor(() => expect(agentApi.register).toHaveBeenCalledWith('CLAUDE', ['DEV'], 'sk-test-key'));
  });

  it('eksik bir yetenek rozetine tiklamak addCapability cagirir', async () => {
    vi.mocked(agentApi.list).mockResolvedValue(page);
    vi.mocked(agentApi.addCapability).mockResolvedValue(undefined);

    renderWithProviders(<AgentsPanel />);
    await screen.findByText('ONLINE');

    fireEvent.click(screen.getByTitle('DEPLOY yeteneği ekle'));

    await waitFor(() => expect(agentApi.addCapability).toHaveBeenCalledWith('agent-12345678', 'DEPLOY'));
  });

  it('kaldir tiklaninca remove cagirir', async () => {
    vi.mocked(agentApi.list).mockResolvedValue(page);
    vi.mocked(agentApi.remove).mockResolvedValue(undefined);

    renderWithProviders(<AgentsPanel />);
    await screen.findByText('ONLINE');

    fireEvent.click(screen.getByText('KALDIR'));

    await waitFor(() => expect(agentApi.remove).toHaveBeenCalledWith('agent-12345678'));
  });
});
