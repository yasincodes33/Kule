import { QueryClientProvider } from '@tanstack/react-query';
import { Navigate, Route, BrowserRouter, Routes } from 'react-router-dom';
import LoginPage from '../features/auth/LoginPage';
import ForgotPasswordPage from '../features/auth/ForgotPasswordPage';
import ResetPasswordPage from '../features/auth/ResetPasswordPage';
import CreateOrgPage from '../features/organizations/CreateOrgPage';
import AppShell from '../features/layout/AppShell';
import BoardPage from '../features/tasks/BoardPage';
import ProjectsPage from '../features/projects/ProjectsPage';
import ProfilePage from '../features/profile/ProfilePage';
import NotificationsPage from '../features/notifications/NotificationsPage';
import AuditLogPage from '../features/audit/AuditLogPage';
import TaskDetailPage from '../features/tasks/TaskDetailPage';
import ApprovalsPage from '../features/approvals/ApprovalsPage';
import RunnersPage from '../features/runners/RunnersPage';
import OrganizationPage from '../features/organizations/OrganizationPage';
import { ToastProvider } from '../shared/components/Toasts';
import { useAuthStore } from '../shared/auth/authStore';
import Bootstrap from './Bootstrap';
import RequireAuth from './RequireAuth';
import { queryClient } from './queryClient';

function LoginRoute() {
  const accessToken = useAuthStore((s) => s.accessToken);
  if (accessToken) return <Navigate to="/" replace />;
  return <LoginPage />;
}

function OnboardingRoute() {
  const accessToken = useAuthStore((s) => s.accessToken);
  if (!accessToken) return <Navigate to="/login" replace />;
  return <CreateOrgPage />;
}

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <BrowserRouter>
          <Bootstrap>
            <Routes>
              <Route path="/login" element={<LoginRoute />} />
              <Route path="/forgot-password" element={<ForgotPasswordPage />} />
              <Route path="/reset-password" element={<ResetPasswordPage />} />
              <Route path="/onboarding" element={<OnboardingRoute />} />
              <Route element={<RequireAuth />}>
                <Route element={<AppShell />}>
                  <Route index element={<BoardPage />} />
                  <Route path="projects" element={<ProjectsPage />} />
                  <Route path="tasks/:taskId" element={<TaskDetailPage />} />
                  <Route path="approvals" element={<ApprovalsPage />} />
                  <Route path="runners" element={<RunnersPage />} />
                  <Route path="organization" element={<OrganizationPage />} />
                  <Route path="profile" element={<ProfilePage />} />
                  <Route path="notifications" element={<NotificationsPage />} />
                  <Route path="audit-log" element={<AuditLogPage />} />
                </Route>
              </Route>
              <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
          </Bootstrap>
        </BrowserRouter>
      </ToastProvider>
    </QueryClientProvider>
  );
}
