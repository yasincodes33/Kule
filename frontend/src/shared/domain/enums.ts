// Kule — backend enum'larıyla birebir eşleşen görüntüleme meta verisi.
// Enum değerleri backend'deki com.AgentSaasAplication.*.domain paketleriyle senkron
// tutulmalı (bkz. TaskStatus, Role, UsedAgent, AgentType, ApprovalStatus, RunnerStatus).

export type TaskStatus =
  | 'QUEUED'
  | 'DISPATCHED'
  | 'RUNNING'
  | 'AWAITING_APPROVAL'
  | 'COMPLETED'
  | 'FAILED'
  | 'REJECTED'
  | 'CANCELLED';

export const STATUS_ORDER: TaskStatus[] = [
  'QUEUED',
  'DISPATCHED',
  'RUNNING',
  'AWAITING_APPROVAL',
  'COMPLETED',
  'FAILED',
  'REJECTED',
  'CANCELLED',
];

export const STATUS: Record<TaskStatus, { label: string; c: string }> = {
  QUEUED: { label: 'QUEUED', c: 'var(--st-queue)' },
  DISPATCHED: { label: 'DISPATCHED', c: 'var(--st-disp)' },
  RUNNING: { label: 'RUNNING', c: 'var(--st-run)' },
  AWAITING_APPROVAL: { label: 'AWAITING_APPROVAL', c: 'var(--st-wait)' },
  COMPLETED: { label: 'COMPLETED', c: 'var(--st-ok)' },
  FAILED: { label: 'FAILED', c: 'var(--st-fail)' },
  REJECTED: { label: 'REJECTED', c: 'var(--st-rej)' },
  CANCELLED: { label: 'CANCELLED', c: 'var(--st-cancel)' },
};

export type TaskType = 'DEV' | 'ANALYSIS' | 'DEPLOY';
export const TASK_TYPES: TaskType[] = ['DEV', 'ANALYSIS', 'DEPLOY'];

export type ModelTier = 'BUDGET' | 'DEFAULT' | 'REASONING';
export const MODEL_TIERS: ModelTier[] = ['BUDGET', 'DEFAULT', 'REASONING'];

// Task.usedAgent — görevi fiilen çözen araç (runner kaydında seçilmiyor, isteğe bağlı bildirilir).
export type UsedAgent = 'CLAUDE_CODE' | 'ANTIGRAVITY' | 'HERMES' | 'OPENCLAW' | 'OMNIROUTE' | 'MANUAL';
export const USED_AGENT: Record<UsedAgent, { name: string; mark: string; c: string }> = {
  CLAUDE_CODE: { name: 'Claude Code', mark: 'CC', c: 'var(--ag-claude)' },
  ANTIGRAVITY: { name: 'Antigravity', mark: 'AG', c: 'var(--ag-gem)' },
  HERMES: { name: 'Hermes', mark: 'HM', c: 'var(--ag-gpt)' },
  OPENCLAW: { name: 'OpenClaw', mark: 'OC', c: 'var(--st-disp)' },
  OMNIROUTE: { name: 'Omniroute', mark: 'OR', c: 'var(--color-accent-300)' },
  MANUAL: { name: 'Manuel', mark: 'MN', c: 'var(--ink-3)' },
};

// AgentConnection.agentType — org'a bağlanan bulut ajan hesapları (agents sayfası).
export type AgentType = 'CLAUDE' | 'CHATGPT' | 'GEMINI';
export const AGENT_TYPE: Record<AgentType, { name: string; mark: string; c: string }> = {
  CLAUDE: { name: 'Claude', mark: 'CL', c: 'var(--ag-claude)' },
  CHATGPT: { name: 'ChatGPT', mark: 'GP', c: 'var(--ag-gpt)' },
  GEMINI: { name: 'Gemini', mark: 'GM', c: 'var(--ag-gem)' },
};

export type AgentStatus = 'ONLINE' | 'OFFLINE';
export type RunnerStatus = 'ONLINE' | 'OFFLINE';

export type Role = 'OWNER' | 'ADMIN' | 'APPROVER' | 'DEVELOPER' | 'VIEWER';
export const ROLES: Record<Role, { tr: string; note: string }> = {
  OWNER: { tr: 'Sahip', note: 'Faturalama, organizasyon silme' },
  ADMIN: { tr: 'Yönetici', note: 'Üye, runner ve politika yönetimi' },
  APPROVER: { tr: 'Onaylayıcı', note: 'Riskli araç çağrılarını onaylar' },
  DEVELOPER: { tr: 'Geliştirici', note: 'Görev açar, log okur' },
  VIEWER: { tr: 'İzleyici', note: 'Yalnızca okuma' },
};
export const ROLE_ORDER: Role[] = ['OWNER', 'ADMIN', 'APPROVER', 'DEVELOPER', 'VIEWER'];

export type MembershipStatus = 'PENDING' | 'ACTIVE' | 'REVOKED';

export type ApprovalStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'EXPIRED';

export type NotificationType =
  | 'MEMBERSHIP_INVITED'
  | 'APPROVAL_REQUESTED'
  | 'APPROVAL_APPROVED'
  | 'APPROVAL_EXPIRED'
  | 'APPROVAL_REJECTED'
  | 'TASK_COMPLETED'
  | 'TASK_FAILED'
  | 'TERMINAL_SESSION_REQUESTED'
  | 'TERMINAL_SESSION_APPROVED'
  | 'TERMINAL_SESSION_REJECTED';

/** Bildirim tipine göre görünen metin + tıklanınca gidilecek yer — payload alanları
 * NotificationEventListener'ın ürettiği gerçek anahtarlarla birebir (membershipId/role,
 * approvalId/taskId, taskId/title). */
export function describeNotification(type: NotificationType, payload: Record<string, unknown>): { text: string; link: string | null; c: string } {
  const str = (k: string) => (typeof payload[k] === 'string' ? (payload[k] as string) : '');
  switch (type) {
    case 'MEMBERSHIP_INVITED':
      return { text: `Bir organizasyona ${str('role')} rolüyle davet edildin`, link: '/organization', c: 'var(--color-accent)' };
    case 'APPROVAL_REQUESTED':
      return { text: 'Onayını bekleyen riskli bir araç çağrısı var', link: '/approvals', c: 'var(--color-accent)' };
    case 'APPROVAL_APPROVED':
      return { text: 'Onay talebin onaylandı, görev sürüyor', link: `/tasks/${str('taskId')}`, c: 'var(--st-ok)' };
    case 'APPROVAL_REJECTED':
      return { text: 'Onay talebin reddedildi', link: `/tasks/${str('taskId')}`, c: 'var(--st-fail)' };
    case 'APPROVAL_EXPIRED':
      return { text: 'Onay talebinin süresi doldu', link: `/tasks/${str('taskId')}`, c: 'var(--st-wait)' };
    case 'TASK_COMPLETED':
      return { text: `Görev tamamlandı: ${str('title')}`, link: `/tasks/${str('taskId')}`, c: 'var(--st-ok)' };
    case 'TASK_FAILED':
      return { text: `Görev başarısız oldu: ${str('title')}`, link: `/tasks/${str('taskId')}`, c: 'var(--st-fail)' };
    case 'TERMINAL_SESSION_REQUESTED':
      return { text: 'Bir runner için canlı terminal erişimi isteniyor', link: '/runners', c: 'var(--color-accent)' };
    case 'TERMINAL_SESSION_APPROVED':
      return { text: 'Terminal oturum isteğin onaylandı, bağlanabilirsin', link: '/runners', c: 'var(--st-ok)' };
    case 'TERMINAL_SESSION_REJECTED':
      return { text: 'Terminal oturum isteğin reddedildi', link: '/runners', c: 'var(--st-fail)' };
    default:
      return { text: type, link: null, c: 'var(--ink-2)' };
  }
}

// TaskLog.level — runner/ajan tarafından serbest metin olarak set ediliyor (backend'de sabit
// bir enum yok, bkz. TaskStateService.appendLog). Bilinen değerler için renk, bilinmeyen için
// nötr bir varsayılan.
const LOG_LEVEL_COLOR: Record<string, string> = {
  INFO: 'var(--ink-2)',
  SYS: 'var(--ink-3)',
  TOOL: 'var(--st-disp)',
  AGENT: 'var(--ink-1)',
  // Canlı terminalde bir AI aracıyla ("bu araçla başlat") üretilen çıktının oturum kapanınca
  // tek seferde yazılan kalıcı kaydı — bkz. backend AgentBridgeHandler.flushTranscriptToTaskLog.
  MODEL: 'var(--color-accent)',
  // Masaüstü uygulamasındaki gömülü terminalin canlı akan kaydı — görevi çalıştıran kişinin
  // ekranda GÖRDÜĞÜ metnin aynısı (bkz. agentsaas-desktop task-window.js collectTerminalLines).
  TERMINAL: 'var(--st-disp)',
  // API anahtarlı bulut ajanlarının ürettiği yardımcı kayıtlar: görev planı (TRIAGE),
  // kod incelemesi (REVIEW) ve terminal kaydının özeti (SUMMARY).
  TRIAGE: 'var(--st-wait)',
  REVIEW: 'var(--color-accent)',
  SUMMARY: 'var(--st-ok)',
  OK: 'var(--st-ok)',
  SUCCESS: 'var(--st-ok)',
  WARN: 'var(--st-wait)',
  WARNING: 'var(--st-wait)',
  ERROR: 'var(--st-fail)',
  ERR: 'var(--st-fail)',
  GATE: 'var(--color-accent)',
};

export function logLevelColor(level: string): string {
  return LOG_LEVEL_COLOR[level.toUpperCase()] ?? 'var(--ink-2)';
}
