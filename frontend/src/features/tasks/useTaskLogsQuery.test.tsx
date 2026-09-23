import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { TaskLogResponse } from '../../shared/api/types';
import { taskApi } from './api';
import { useTaskLogsQuery } from './hooks';

vi.mock('./api', () => ({
  taskApi: { logs: vi.fn() },
}));

function LogCount() {
  const q = useTaskLogsQuery('task-1');
  return <div data-testid="count">{q.data ? q.data.length : 'yok'}</div>;
}

const logLine = (id: string, level: string): TaskLogResponse => ({
  id,
  taskId: 'task-1',
  level,
  message: `satir ${id}`,
  timestamp: new Date().toISOString(),
});

/**
 * Canlı hatanın regresyon testi: görev logları `staleTime: Infinity` ile önbelleğe alınıyordu.
 * Kullanıcı görev sayfasını açıp (o anki log anlık görüntüsü önbelleğe girer), masaüstünde
 * çalışıp canlı akan TERMINAL satırlarını görüyor, sayfadan çıkıp geri döndüğünde ise
 * react-query ESKİ anlık görüntüyü döndürdüğü — ve canlı akış state'i unmount'ta silindiği —
 * için satırlar kayboluyordu.
 */
describe('useTaskLogsQuery', () => {
  beforeEach(() => vi.clearAllMocks());

  it('sayfaya geri dönüldügünde log geçmisini YENİDEN çeker', async () => {
    // gcTime varsayılanda (5 dk) — yani önbellek girdisi iki mount arasında yaşıyor.
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const wrap = () => render(
      <QueryClientProvider client={queryClient}><LogCount /></QueryClientProvider>,
    );

    vi.mocked(taskApi.logs).mockResolvedValue([logLine('1', 'INFO')]);
    const first = wrap();
    await waitFor(() => expect(screen.getByTestId('count')).toHaveTextContent('1'));
    first.unmount();

    // Bu arada masaüstü terminal loglarını akıttı: sunucuda artık 3 satır var.
    vi.mocked(taskApi.logs).mockResolvedValue([
      logLine('1', 'INFO'), logLine('2', 'TERMINAL'), logLine('3', 'TERMINAL'),
    ]);

    wrap();
    await waitFor(() => expect(screen.getByTestId('count')).toHaveTextContent('3'));
    expect(taskApi.logs).toHaveBeenCalledTimes(2);
  });
});
