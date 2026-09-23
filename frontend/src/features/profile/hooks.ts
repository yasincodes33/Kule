import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useAuthStore } from '../../shared/auth/authStore';
import { profileApi } from './api';

export function useUpdateProfileMutation() {
  const queryClient = useQueryClient();
  const setUser = useAuthStore((s) => s.setUser);
  return useMutation({
    mutationFn: (displayName: string) => profileApi.updateDisplayName(displayName),
    onSuccess: (me) => {
      setUser(me);
      queryClient.setQueryData(['me'], me);
    },
  });
}

export function useChangePasswordMutation() {
  return useMutation({
    mutationFn: ({ currentPassword, newPassword }: { currentPassword: string; newPassword: string }) =>
      profileApi.changePassword(currentPassword, newPassword),
  });
}
