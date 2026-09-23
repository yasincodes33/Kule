import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useAuthStore } from '../../shared/auth/authStore';
import { projectApi } from './api';

export function useProjectsListQuery() {
  const activeOrgId = useAuthStore((s) => s.activeOrgId);
  return useQuery({
    queryKey: ['projects', activeOrgId],
    queryFn: () => projectApi.listForOrg(activeOrgId as string),
    enabled: !!activeOrgId,
    select: (page) => page.content,
  });
}

export function useCreateProjectMutation() {
  const activeOrgId = useAuthStore((s) => s.activeOrgId);
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ name, repoUrl, defaultBranch }: { name: string; repoUrl: string; defaultBranch: string }) =>
      projectApi.create(activeOrgId as string, name, repoUrl, defaultBranch),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['projects', activeOrgId] }),
  });
}

export function useUpdateProjectMutation() {
  const activeOrgId = useAuthStore((s) => s.activeOrgId);
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ projectId, name, repoUrl, defaultBranch }: { projectId: string; name: string; repoUrl: string; defaultBranch: string }) =>
      projectApi.update(projectId, name, repoUrl, defaultBranch),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['projects', activeOrgId] }),
  });
}
