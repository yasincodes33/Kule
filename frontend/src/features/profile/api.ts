import { api } from '../../shared/api/client';
import type { Me } from '../../shared/api/types';

export const profileApi = {
  updateDisplayName: (displayName: string) => api.put<Me>('/me', { displayName }, { withOrg: false }),
  changePassword: (currentPassword: string, newPassword: string) =>
    api.post<void>('/me/change-password', { currentPassword, newPassword }, { withOrg: false }),
};
