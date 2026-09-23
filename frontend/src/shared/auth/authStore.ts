import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { AuthResponse, Me, OrganizationResponse } from '../api/types';

interface AuthState {
  accessToken: string | null;
  user: Me | null;
  organizations: OrganizationResponse[];
  activeOrgId: string | null;
  bootstrapped: boolean;

  setSession: (auth: AuthResponse) => void;
  setUser: (user: Me) => void;
  setOrganizations: (orgs: OrganizationResponse[]) => void;
  setActiveOrgId: (id: string) => void;
  setBootstrapped: (v: boolean) => void;
  clear: () => void;
}

// refreshToken artık burada hiç tutulmuyor — httpOnly bir cookie'de (JS'in
// erişemediği yerde) taşınıyor, bkz. shared/api/types.ts. Yalnızca activeOrgId localStorage'da
// kalıcı. accessToken ve user kasıtlı olarak kalıcı değil: her sayfa yüklemesinde tarayıcının
// otomatik gönderdiği cookie'yle /auth/refresh + /me üzerinden taze alınır (bkz.
// app/Bootstrap.tsx, shared/api/client.ts:refreshAccessToken).
export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      accessToken: null,
      user: null,
      organizations: [],
      activeOrgId: null,
      bootstrapped: false,

      setSession: (auth) => set({ accessToken: auth.accessToken }),
      setUser: (user) => set({ user }),
      setOrganizations: (organizations) =>
        set((s) => ({
          organizations,
          activeOrgId: s.activeOrgId && organizations.some((o) => o.id === s.activeOrgId)
            ? s.activeOrgId
            : (organizations[0]?.id ?? null),
        })),
      setActiveOrgId: (activeOrgId) => set({ activeOrgId }),
      setBootstrapped: (bootstrapped) => set({ bootstrapped }),
      clear: () => set({ accessToken: null, user: null, organizations: [], activeOrgId: null }),
    }),
    {
      name: 'kule-auth',
      partialize: (s) => ({ activeOrgId: s.activeOrgId }),
    },
  ),
);
