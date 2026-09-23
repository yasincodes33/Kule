import { useQuery } from '@tanstack/react-query';
import { useAuthStore } from '../../shared/auth/authStore';
import { auditApi } from './api';

export function useAuditLogQuery() {
  const activeOrgId = useAuthStore((s) => s.activeOrgId);
  return useQuery({
    queryKey: ['audit-logs', activeOrgId],
    queryFn: () => auditApi.list(activeOrgId as string),
    select: (page) => page.content,
    enabled: !!activeOrgId,
  });
}
