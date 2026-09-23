import { api } from '../../shared/api/client';
import type { Me, MembershipResponse, OrganizationResponse, Page, PendingInvitationResponse } from '../../shared/api/types';
import type { Role } from '../../shared/domain/enums';

export const orgApi = {
  me: () => api.get<Me>('/me', { withOrg: false }),
  listMine: () => api.get<OrganizationResponse[]>('/me/organizations', { withOrg: false }),
  create: (name: string) => api.post<OrganizationResponse>('/organizations', { name }, { withOrg: false }),
};

export const membershipApi = {
  list: (organizationId: string) =>
    api.get<Page<MembershipResponse>>(`/organizations/${organizationId}/memberships?size=100`),
  invite: (organizationId: string, email: string, role: Role) =>
    api.post<MembershipResponse>(`/organizations/${organizationId}/memberships/invite`, { email, role }),
  revoke: (organizationId: string, membershipId: string) =>
    api.del<void>(`/organizations/${organizationId}/memberships/${membershipId}`),
  transferOwnership: (organizationId: string, newOwnerUserId: string) =>
    api.post<void>(`/organizations/${organizationId}/memberships/transfer-ownership`, { newOwnerUserId }),
};

// Daveti gönderen taraf (yukarıdaki membershipApi) zaten bağlıydı, ama davet
// edilen tarafın daveti görüp kabul/red edebileceği hiçbir uç frontend'den hiç çağrılmıyordu —
// backend'de vardı ama kullanıcı bir daveti kabul edecek ekran bulamıyordu. Bu uçların hiçbiri
// org bağlamına ait değil (bkz. TenantFilter'ın `/me/invitations` + `/invitations/**` istisnası).
export const invitationApi = {
  listMine: () => api.get<PendingInvitationResponse[]>('/me/invitations', { withOrg: false }),
  accept: (membershipId: string) =>
    api.post<MembershipResponse>(`/invitations/${membershipId}/accept`, undefined, { withOrg: false }),
  reject: (membershipId: string) =>
    api.post<void>(`/invitations/${membershipId}/reject`, undefined, { withOrg: false }),
};
