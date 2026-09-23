import { useAuthStore } from '../auth/authStore';
import type { AuthResponse, ErrorResponse } from './types';

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8081/api/v1';

export class ApiError extends Error {
  status: number;
  fieldErrors: Record<string, string> | null;
  constructor(res: ErrorResponse) {
    super(res.message);
    this.status = res.status;
    this.fieldErrors = res.fieldErrors;
  }
}

// Aynı anda birden çok istek 401 alırsa tek bir refresh çağrısını paylaşır — her biri
// kendi /auth/refresh isteğini açmaz.
let refreshInFlight: Promise<string | null> | null = null;

// refreshToken artık JS'ten okunamıyor (httpOnly cookie) — bu yüzden burada
// varlığı kontrol edilemez, credentials:'include' ile tarayıcının cookie'yi otomatik göndermesine
// güveniliyor. Cookie hiç yoksa/geçersizse backend 401 döner, aşağıdaki catch devreye girer.
async function refreshAccessToken(): Promise<string | null> {
  const { clear, setSession } = useAuthStore.getState();
  if (!refreshInFlight) {
    refreshInFlight = fetch(`${API_BASE}/auth/refresh`, {
      method: 'POST',
      credentials: 'include',
    })
      .then(async (res) => {
        if (!res.ok) {
          clear();
          return null;
        }
        const auth = (await res.json()) as AuthResponse;
        setSession(auth);
        return auth.accessToken;
      })
      .catch(() => {
        clear();
        return null;
      })
      .finally(() => {
        refreshInFlight = null;
      });
  }
  return refreshInFlight;
}

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  body?: unknown;
  /** Org'dan bağımsız uçlar için false ver (auth/*, /me, /me/organizations, /invitations/*). */
  withOrg?: boolean;
  signal?: AbortSignal;
}

async function request<T>(path: string, opts: RequestOptions = {}, isRetry = false): Promise<T> {
  const { accessToken, activeOrgId } = useAuthStore.getState();
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (accessToken) headers.Authorization = `Bearer ${accessToken}`;
  if (opts.withOrg !== false && activeOrgId) headers['X-Organization-Id'] = activeOrgId;

  const res = await fetch(`${API_BASE}${path}`, {
    method: opts.method ?? 'GET',
    headers,
    body: opts.body !== undefined ? JSON.stringify(opts.body) : undefined,
    signal: opts.signal,
    credentials: 'include',
  });

  if (res.status === 401 && !isRetry && accessToken) {
    const newToken = await refreshAccessToken();
    if (newToken) return request<T>(path, opts, true);
  }

  if (res.status === 204) return undefined as T;

  const text = await res.text();
  const data = text ? JSON.parse(text) : undefined;

  if (!res.ok) {
    throw new ApiError(
      data ?? { status: res.status, message: `İstek başarısız (${res.status})`, timestamp: new Date().toISOString(), fieldErrors: null },
    );
  }
  return data as T;
}

export const api = {
  get: <T>(path: string, opts?: Omit<RequestOptions, 'method' | 'body'>) => request<T>(path, { ...opts, method: 'GET' }),
  post: <T>(path: string, body?: unknown, opts?: Omit<RequestOptions, 'method' | 'body'>) =>
    request<T>(path, { ...opts, method: 'POST', body }),
  put: <T>(path: string, body?: unknown, opts?: Omit<RequestOptions, 'method' | 'body'>) =>
    request<T>(path, { ...opts, method: 'PUT', body }),
  patch: <T>(path: string, body?: unknown, opts?: Omit<RequestOptions, 'method' | 'body'>) =>
    request<T>(path, { ...opts, method: 'PATCH', body }),
  del: <T>(path: string, opts?: Omit<RequestOptions, 'method' | 'body'>) => request<T>(path, { ...opts, method: 'DELETE' }),
};

export { API_BASE };
