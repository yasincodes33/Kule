import { Navigate, Outlet } from 'react-router-dom';
import { useMeQuery, useOrganizationsQuery } from '../features/organizations/hooks';
import { useAuthStore } from '../shared/auth/authStore';

export default function RequireAuth() {
  const accessToken = useAuthStore((s) => s.accessToken);
  const activeOrgId = useAuthStore((s) => s.activeOrgId);
  const meQuery = useMeQuery();
  const orgsQuery = useOrganizationsQuery();

  if (!accessToken) return <Navigate to="/login" replace />;

  if (meQuery.isLoading || orgsQuery.isLoading) return null;

  if ((orgsQuery.data ?? []).length === 0) return <Navigate to="/onboarding" replace />;

  if (!activeOrgId) return null;

  return <Outlet />;
}
