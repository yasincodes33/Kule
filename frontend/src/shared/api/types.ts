// Backend DTO record'larıyla birebir — src/main/java/com/AgentSaasAplication/**/dto/*.java

import type {
  AgentStatus,
  AgentType,
  ApprovalStatus,
  MembershipStatus,
  NotificationType,
  Role,
  RunnerStatus,
  TaskStatus,
  TaskType,
  UsedAgent,
} from '../domain/enums';

export interface ErrorResponse {
  status: number;
  message: string;
  timestamp: string;
  fieldErrors: Record<string, string> | null;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
}

// refreshToken artık JSON body'de dönmüyor — httpOnly bir cookie'de taşınıyor
// (bkz. backend AuthController), JS'in erişemediği bir yerde. Bu sayede bir XSS açığı bu token'ı
// hiç okuyamaz.
export interface AuthResponse {
  accessToken: string;
  expiresIn: number;
}

export interface Me {
  id: string;
  email: string;
  displayName: string;
}

export interface OrganizationResponse {
  id: string;
  name: string;
  slug: string;
}

export interface MembershipResponse {
  id: string;
  organizationId: string;
  userId: string | null;
  invitedEmail: string | null;
  role: Role;
  status: MembershipStatus;
}

// `/me/invitations`'a özel — davet edilen kullanıcı henüz üye olmadığı için
// `organizations` listesinde bu organizasyonun adını göremez, backend burada ayrıca gönderiyor.
export interface PendingInvitationResponse {
  membershipId: string;
  organizationId: string;
  organizationName: string | null;
  role: Role;
}

export interface ProjectResponse {
  id: string;
  name: string;
  repoUrl: string;
  defaultBranch: string;
}

export interface TaskResponse {
  id: string;
  projectId: string;
  agentConnectionId: string | null;
  type: TaskType;
  status: TaskStatus;
  title: string;
  retryCount: number;
  assignedUserId: string | null;
  runnerConnectionId: string | null;
  usedAgent: UsedAgent | null;
  prompt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface TaskLogResponse {
  id: string;
  taskId: string;
  level: string;
  message: string;
  timestamp: string;
}

export interface RunnerConnectionResponse {
  id: string;
  ownerUserId: string;
  projectId: string | null;
  label: string;
  status: RunnerStatus;
  lastHeartbeatAt: string | null;
  capabilities: TaskType[];
}

export interface RunnerTokenResponse {
  token: string;
}

export interface RunnerTerminalSessionResponse {
  id: string;
  runnerConnectionId: string;
  requestedBy: string;
  status: ApprovalStatus;
  decidedBy: string | null;
  decidedAt: string | null;
  expiresAt: string;
  createdAt: string;
}

export interface WsTicketResponse {
  ticket: string;
}

export interface AgentConnectionResponse {
  id: string;
  agentType: AgentType;
  status: AgentStatus;
  capabilities: TaskType[];
}

export interface ApprovalResponse {
  id: string;
  taskId: string;
  /** NULL olabilir: onayı tetikleyen araç çağrısı bir web kullanıcısına değil, göreve bağlı
   *  (ToolCallApprovalGate'e `task.getAssignedUserId()` geçiyor ve o da opsiyonel) — DB'de de
   *  `requested_by UUID REFERENCES users(id)`, NOT NULL değil. */
  requestedBy: string | null;
  status: ApprovalStatus;
  decidedBy: string | null;
  decidedAt: string | null;
  expiresAt: string;
  createdAt: string;
  /** Onaya sebep olan araç çağrısı ve argümanları — onay ekranı "neyi onaylıyorum"
   *  sorusunu bunlarla cevaplıyor. Elle istenen onaylarda ve V23 öncesi kayıtlarda null. */
  toolName: string | null;
  toolArguments: string | null;
}

export interface NotificationResponse {
  id: string;
  type: NotificationType;
  payload: Record<string, unknown>;
  read: boolean;
  createdAt: string;
}

export interface AuditLogResponse {
  id: string;
  actorUserId: string | null;
  action: string;
  entityType: string;
  entityId: string | null;
  metadata: Record<string, unknown> | null;
  createdAt: string;
}
