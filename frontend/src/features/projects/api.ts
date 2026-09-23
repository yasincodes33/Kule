import { api } from '../../shared/api/client';
import type { Page, ProjectResponse } from '../../shared/api/types';

export const projectApi = {
  listForOrg: (organizationId: string) =>
    api.get<Page<ProjectResponse>>(`/organizations/${organizationId}/projects?size=100`),
  get: (projectId: string) => api.get<ProjectResponse>(`/projects/${projectId}`),
  create: (organizationId: string, name: string, repoUrl: string, defaultBranch: string) =>
    api.post<ProjectResponse>(`/organizations/${organizationId}/projects`, { name, repoUrl, defaultBranch }),
  update: (projectId: string, name: string, repoUrl: string, defaultBranch: string) =>
    api.put<ProjectResponse>(`/projects/${projectId}`, { name, repoUrl, defaultBranch }),
};
