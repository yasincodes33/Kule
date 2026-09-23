import { api } from '../../shared/api/client';
import type { AuditLogResponse, Page } from '../../shared/api/types';

export const auditApi = {
  // Backend zaten createdAt DESC ile döndürüyor (findByOrganizationIdOrderByCreatedAtDesc).
  list: (organizationId: string) => api.get<Page<AuditLogResponse>>(`/organizations/${organizationId}/audit-logs?size=100`),
};
