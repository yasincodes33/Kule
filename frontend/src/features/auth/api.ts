import { api } from '../../shared/api/client';
import type { AuthResponse } from '../../shared/api/types';

export const authApi = {
  login: (email: string, password: string) =>
    api.post<AuthResponse>('/auth/login', { email, password }, { withOrg: false }),
  register: (email: string, password: string, displayName: string) =>
    api.post<AuthResponse>('/auth/register', { email, password, displayName: displayName || null }, { withOrg: false }),
  // refreshToken artık httpOnly cookie'de taşınıyor — tarayıcı otomatik gönderiyor, body'de yok.
  refresh: () => api.post<AuthResponse>('/auth/refresh', undefined, { withOrg: false }),
  forgotPassword: (email: string) =>
    api.post<void>('/auth/forgot-password', { email }, { withOrg: false }),
  resetPassword: (token: string, newPassword: string) =>
    api.post<void>('/auth/reset-password', { token, newPassword }, { withOrg: false }),
  logout: () => api.post<void>('/auth/logout', undefined, { withOrg: false }),
};
