import { act, renderHook, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useAuthStore } from '../../shared/auth/authStore';
import { taskApi } from './api';
import { useTaskLogStream } from './useTaskLogStream';

vi.mock('./api', () => ({
  taskApi: { issueLogsWsTicket: vi.fn() },
}));

/** Testler boyunca kurulan tüm sahte WebSocket'leri (gerçek bağlantı açılmadan) izlemek için. */
class FakeWebSocket {
  static instances: FakeWebSocket[] = [];
  url: string;
  onopen: (() => void) | null = null;
  onclose: (() => void) | null = null;
  onerror: (() => void) | null = null;
  onmessage: ((event: { data: string }) => void) | null = null;
  closed = false;

  constructor(url: string) {
    this.url = url;
    FakeWebSocket.instances.push(this);
  }

  close() {
    this.closed = true;
  }
}

/**
 * Canlı görev logu akışı. WS URL'sinde asıl access token değil,
 * `taskApi.issueLogsWsTicket`'ten alınan tek kullanımlık bir bilet taşınır; bağlantı
 * koptuğunda 3 saniye sonra yeni bir bilet alınarak otomatik yeniden bağlanılır.
 */
describe('useTaskLogStream', () => {
  const originalWebSocket = globalThis.WebSocket;

  beforeEach(() => {
    vi.clearAllMocks();
    FakeWebSocket.instances = [];
    // @ts-expect-error test ortamında gerçek WebSocket yok/istemiyoruz
    globalThis.WebSocket = FakeWebSocket;
    useAuthStore.setState({ accessToken: 'gercek-access-token-asla-url-de-olmamali' });
    vi.mocked(taskApi.issueLogsWsTicket).mockResolvedValue({ ticket: 'tek-kullanimlik-bilet' });
  });

  afterEach(() => {
    globalThis.WebSocket = originalWebSocket;
    vi.useRealTimers();
  });

  it('accessToken veya taskId yoksa hic baglanti denemez', () => {
    useAuthStore.setState({ accessToken: null });
    renderHook(() => useTaskLogStream('task-1'));

    expect(taskApi.issueLogsWsTicket).not.toHaveBeenCalled();
  });

  it('once bilet alir, sonra WS URLsine SADECE bileti koyar — access token asla URLde gorunmez', async () => {
    renderHook(() => useTaskLogStream('task-1'));

    await waitFor(() => expect(taskApi.issueLogsWsTicket).toHaveBeenCalledWith('task-1'));
    await waitFor(() => expect(FakeWebSocket.instances).toHaveLength(1));

    const url = FakeWebSocket.instances[0].url;
    expect(url).toContain('ticket=tek-kullanimlik-bilet');
    expect(url).not.toContain('gercek-access-token-asla-url-de-olmamali');
    expect(url).not.toContain('token=');
  });

  it('onopen tetiklenince connected true olur', async () => {
    const { result } = renderHook(() => useTaskLogStream('task-1'));
    await waitFor(() => expect(FakeWebSocket.instances).toHaveLength(1));

    act(() => FakeWebSocket.instances[0].onopen?.());

    await waitFor(() => expect(result.current.connected).toBe(true));
  });

  it('gelen mesajlar live listesine eklenir', async () => {
    const { result } = renderHook(() => useTaskLogStream('task-1'));
    await waitFor(() => expect(FakeWebSocket.instances).toHaveLength(1));

    act(() =>
      FakeWebSocket.instances[0].onmessage?.({
        data: JSON.stringify({ id: 'log-1', level: 'INFO', message: 'merhaba', timestamp: '2026-01-01T00:00:00Z', status: null }),
      }),
    );

    await waitFor(() => expect(result.current.live).toHaveLength(1));
    expect(result.current.live[0].message).toBe('merhaba');
  });

  it('bozuk (JSON olmayan) bir mesaj gelirse hata firlatmaz ve listeyi degistirmez', async () => {
    const { result } = renderHook(() => useTaskLogStream('task-1'));
    await waitFor(() => expect(FakeWebSocket.instances).toHaveLength(1));

    act(() => FakeWebSocket.instances[0].onmessage?.({ data: 'json-degil' }));

    expect(result.current.live).toHaveLength(0);
  });

  it('baglanti koptugunda connected false olur ve 3sn sonra yeni bir bilet alip yeniden baglanir', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const { result } = renderHook(() => useTaskLogStream('task-1'));
    await waitFor(() => expect(FakeWebSocket.instances).toHaveLength(1));
    act(() => FakeWebSocket.instances[0].onopen?.());
    await waitFor(() => expect(result.current.connected).toBe(true));

    act(() => FakeWebSocket.instances[0].onclose?.());
    expect(result.current.connected).toBe(false);

    await act(async () => {
      vi.advanceTimersByTime(3000);
    });

    await waitFor(() => expect(taskApi.issueLogsWsTicket).toHaveBeenCalledTimes(2));
    await waitFor(() => expect(FakeWebSocket.instances).toHaveLength(2));
  });

  it('bilet alma basarisiz olursa 3sn sonra tekrar dener', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    vi.mocked(taskApi.issueLogsWsTicket).mockRejectedValueOnce(new Error('ağ hatası'));

    renderHook(() => useTaskLogStream('task-1'));
    await waitFor(() => expect(taskApi.issueLogsWsTicket).toHaveBeenCalledTimes(1));
    expect(FakeWebSocket.instances).toHaveLength(0);

    await act(async () => {
      vi.advanceTimersByTime(3000);
    });

    await waitFor(() => expect(taskApi.issueLogsWsTicket).toHaveBeenCalledTimes(2));
    await waitFor(() => expect(FakeWebSocket.instances).toHaveLength(1));
  });

  it('unmount olunca acik soketi kapatir', async () => {
    const { unmount } = renderHook(() => useTaskLogStream('task-1'));
    await waitFor(() => expect(FakeWebSocket.instances).toHaveLength(1));

    unmount();

    expect(FakeWebSocket.instances[0].closed).toBe(true);
  });
});
