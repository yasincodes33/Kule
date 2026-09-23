import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render } from '@testing-library/react';
import type { ReactElement } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { ToastProvider } from '../shared/components/Toasts';

/**
 * Sayfa/feature bileşenlerini gerçek QueryClientProvider + Router bağlamıyla render eder —
 * bu olmadan `useQuery`/`useMutation`/`useNavigate` kullanan hiçbir bileşen test edilemez.
 * Her testte TAZE bir QueryClient (retry kapalı — aksi halde başarısız bir mock istek testin
 * kendisini yavaşlatır/zaman aşımına uğratır, gcTime sıfır — testler arası cache sızıntısı olmasın).
 */
export function renderWithProviders(ui: ReactElement, { route = '/' }: { route?: string } = {}) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 }, mutations: { retry: false } },
  });
  return {
    queryClient,
    ...render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={[route]}>
          <ToastProvider>{ui}</ToastProvider>
        </MemoryRouter>
      </QueryClientProvider>,
    ),
  };
}
