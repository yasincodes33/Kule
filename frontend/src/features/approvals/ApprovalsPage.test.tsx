import { fireEvent, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { renderWithProviders } from '../../test/renderWithProviders';
import type { ApprovalResponse, Page, TaskResponse } from '../../shared/api/types';
import { approvalApi } from './api';
import { taskApi } from '../tasks/api';
import ApprovalsPage from './ApprovalsPage';

vi.mock('./api', () => ({
  approvalApi: { listPending: vi.fn(), approve: vi.fn(), reject: vi.fn() },
}));
vi.mock('../tasks/api', () => ({
  taskApi: { get: vi.fn() },
}));

/**
 * Onay kuyruğu ürünün temel güvenlik özelliğidir: riskli araç çağrıları insan onayına
 * bağlanır. Bu testler listenin doğru render edildiğini ve onayla/reddet düğmelerinin
 * doğru API çağrısını tetiklediğini doğrular.
 */
describe('ApprovalsPage', () => {
  const approval: ApprovalResponse = {
    id: 'appr-1',
    taskId: 'task-1',
    requestedBy: 'user-12345678',
    status: 'PENDING',
    decidedBy: null,
    decidedAt: null,
    expiresAt: new Date(Date.now() + 3600_000).toISOString(),
    createdAt: new Date().toISOString(),
    toolName: 'git_push',
    toolArguments: ['remote: origin','branch: main'].join(String.fromCharCode(10)),
  };
  const task: TaskResponse = {
    id: 'task-1',
    projectId: 'proj-1',
    agentConnectionId: null,
    type: 'DEPLOY',
    status: 'AWAITING_APPROVAL',
    title: 'prod deploy',
    retryCount: 0,
    assignedUserId: null,
    runnerConnectionId: null,
    usedAgent: null,
    prompt: null,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  };
  const page: Page<ApprovalResponse> = {
    content: [approval],
    totalElements: 1,
    totalPages: 1,
    number: 0,
    size: 100,
    first: true,
    last: true,
  };

  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(taskApi.get).mockResolvedValue(task);
  });

  it('bekleyen onay yoksa temiz durumu gosterir', async () => {
    vi.mocked(approvalApi.listPending).mockResolvedValue({ ...page, content: [], totalElements: 0 });

    renderWithProviders(<ApprovalsPage />);

    expect(await screen.findByText('Bekleyen onay yok')).toBeInTheDocument();
  });

  it('bekleyen onayi listeler ve detayini gosterir', async () => {
    vi.mocked(approvalApi.listPending).mockResolvedValue(page);

    renderWithProviders(<ApprovalsPage />);

    expect(await screen.findByText('prod deploy')).toBeInTheDocument();
    expect(screen.getByText('Onay ver veya reddet')).toBeInTheDocument();
  });

  /**
   * Canli hatanin regresyon testi: `requestedBy` NULL geldiginde (onay bir web kullanicisindan
   * degil, atanmamis bir gorevin arac cagrisindan dogdugunda) sayfa `null.slice()` ile cokup
   * bomboş bir ekran birakiyordu. Bildirim geliyor ama ekran acilmiyordu.
   */
  it('requestedBy null oldugunda cokmez, sistem talebi olarak gosterir', async () => {
    vi.mocked(approvalApi.listPending).mockResolvedValue({
      ...page,
      content: [{ ...approval, requestedBy: null }],
    });

    renderWithProviders(<ApprovalsPage />);

    expect(await screen.findByText('prod deploy')).toBeInTheDocument();
    expect(screen.getByText('Onay ver veya reddet')).toBeInTheDocument();
    expect(screen.getAllByText(/sistem \(görev\)/).length).toBeGreaterThan(0);
  });

  /**
   * Onay kuyrugu, onaylayan kisiye NEYI onayladigini gostermiyordu: yalnizca gorev basligi ve
   * ham kimlik parcalari vardi. Arac adi ve argumanlari artik hem listede hem detayda goruluyor.
   */
  it('onaylanacak aracin adini ve argumanlarini gosterir', async () => {
    vi.mocked(approvalApi.listPending).mockResolvedValue(page);

    renderWithProviders(<ApprovalsPage />);
    await screen.findByText('prod deploy');

    expect(screen.getAllByText('git_push').length).toBeGreaterThan(0);
    expect(screen.getByText(/remote: origin/)).toBeInTheDocument();
    expect(screen.getByText(/branch: main/)).toBeInTheDocument();
  });

  it('arac bilgisi olmayan eski onaylarda aciklama gosterir', async () => {
    vi.mocked(approvalApi.listPending).mockResolvedValue({
      ...page,
      content: [{ ...approval, toolName: null, toolArguments: null }],
    });

    renderWithProviders(<ApprovalsPage />);

    expect(await screen.findByText('Araç bilgisi yok')).toBeInTheDocument();
  });

  it('onayla butonu dogru task/approval id ile approve cagirir', async () => {
    vi.mocked(approvalApi.listPending).mockResolvedValue(page);
    vi.mocked(approvalApi.approve).mockResolvedValue({ ...approval, status: 'APPROVED' });

    renderWithProviders(<ApprovalsPage />);
    await screen.findByText('prod deploy');

    fireEvent.click(screen.getByText('Onayla ve sürdür'));

    await waitFor(() => expect(approvalApi.approve).toHaveBeenCalledWith('task-1', 'appr-1'));
    expect(approvalApi.reject).not.toHaveBeenCalled();
  });

  it('reddet butonu karar notunu reason olarak reject cagrisina iletir', async () => {
    vi.mocked(approvalApi.listPending).mockResolvedValue(page);
    vi.mocked(approvalApi.reject).mockResolvedValue({ ...approval, status: 'REJECTED' });

    renderWithProviders(<ApprovalsPage />);
    await screen.findByText('prod deploy');

    fireEvent.change(screen.getByPlaceholderText('ör. bakım penceresi dışında'), {
      target: { value: 'bakım penceresi dışında' },
    });
    fireEvent.click(screen.getByText('Reddet'));

    await waitFor(() =>
      expect(approvalApi.reject).toHaveBeenCalledWith('task-1', 'appr-1', 'bakım penceresi dışında'),
    );
  });
});
