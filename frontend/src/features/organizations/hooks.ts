import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useAuthStore } from '../../shared/auth/authStore';
import type { Role } from '../../shared/domain/enums';
import { invitationApi, membershipApi, orgApi } from './api';

export function useMeQuery() {
  const accessToken = useAuthStore((s) => s.accessToken);
  const setUser = useAuthStore((s) => s.setUser);
  return useQuery({
    queryKey: ['me'],
    queryFn: async () => {
      const me = await orgApi.me();
      setUser(me);
      return me;
    },
    enabled: !!accessToken,
    staleTime: 60_000,
  });
}

export function useOrganizationsQuery() {
  const accessToken = useAuthStore((s) => s.accessToken);
  const setOrganizations = useAuthStore((s) => s.setOrganizations);
  return useQuery({
    queryKey: ['organizations'],
    queryFn: async () => {
      const orgs = await orgApi.listMine();
      setOrganizations(orgs);
      return orgs;
    },
    enabled: !!accessToken,
    staleTime: 30_000,
  });
}

export function useCreateOrganizationMutation() {
  const queryClient = useQueryClient();
  const setOrganizations = useAuthStore((s) => s.setOrganizations);
  const setActiveOrgId = useAuthStore((s) => s.setActiveOrgId);
  return useMutation({
    mutationFn: (name: string) => orgApi.create(name),
    onSuccess: async (org) => {
      // invalidateQueries only refetches queries with an ACTIVE observer — on /onboarding
      // nothing is observing ['organizations'] yet (RequireAuth/AppShell aren't mounted), so
      // it would just mark the cache stale without updating it, and the redirect below would
      // read the old empty array. Fetch + seed the cache directly instead.
      const orgs = await orgApi.listMine();
      queryClient.setQueryData(['organizations'], orgs);
      setOrganizations(orgs);
      setActiveOrgId(org.id);
    },
  });
}

export function useMembersQuery(organizationId: string | null) {
  return useQuery({
    queryKey: ['members', organizationId],
    queryFn: () => membershipApi.list(organizationId as string),
    enabled: !!organizationId,
    select: (page) => page.content,
    refetchInterval: 15000,
  });
}

export function useInviteMemberMutation(organizationId: string | null) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ email, role }: { email: string; role: Role }) => membershipApi.invite(organizationId as string, email, role),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['members', organizationId] }),
  });
}

export function useRevokeMemberMutation(organizationId: string | null) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (membershipId: string) => membershipApi.revoke(organizationId as string, membershipId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['members', organizationId] }),
  });
}

export function useTransferOwnershipMutation(organizationId: string | null) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (newOwnerUserId: string) => membershipApi.transferOwnership(organizationId as string, newOwnerUserId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['members', organizationId] }),
  });
}

export function usePendingInvitationsQuery() {
  const accessToken = useAuthStore((s) => s.accessToken);
  return useQuery({
    queryKey: ['pendingInvitations'],
    queryFn: invitationApi.listMine,
    enabled: !!accessToken,
    staleTime: 30_000,
  });
}

export function useAcceptInvitationMutation() {
  const queryClient = useQueryClient();
  const setOrganizations = useAuthStore((s) => s.setOrganizations);
  const setActiveOrgId = useAuthStore((s) => s.setActiveOrgId);
  const activeOrgId = useAuthStore((s) => s.activeOrgId);
  return useMutation({
    mutationFn: (membershipId: string) => invitationApi.accept(membershipId),
    onSuccess: async () => {
      // useCreateOrganizationMutation'daki AYNI sebep: invalidateQueries yalnızca aktif bir
      // observer'ı olan sorguları hemen yeniden çeker — onboarding ekranında ['organizations']
      // henüz izlenmiyor olabilir. Yanıtı doğrudan cache'e yazmak bu race'i baştan önlüyor.
      const orgs = await orgApi.listMine();
      queryClient.setQueryData(['organizations'], orgs);
      setOrganizations(orgs);
      // Kullanıcının daha önce hiç organizasyonu yoktuysa (onboarding akışı), kabul edilen
      // organizasyona otomatik geç — aksi halde RequireAuth activeOrgId bekleyip boş ekranda kalır.
      if (!activeOrgId && orgs.length > 0) setActiveOrgId(orgs[0].id);
      queryClient.invalidateQueries({ queryKey: ['pendingInvitations'] });
    },
  });
}

export function useRejectInvitationMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (membershipId: string) => invitationApi.reject(membershipId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['pendingInvitations'] }),
  });
}
