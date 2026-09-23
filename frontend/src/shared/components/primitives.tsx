import { createElement, type ReactNode } from 'react';
import { AGENT_TYPE, STATUS, USED_AGENT, type AgentType, type Role, type TaskStatus, type UsedAgent } from '../domain/enums';
import { Icon } from './Icon';

export function Dot({ status, live, s = 7 }: { status: TaskStatus; live?: boolean; s?: number }) {
  return <span className={'k-dot' + (live ? ' k-dot-live' : '')} style={{ '--c': STATUS[status].c, width: s, height: s } as React.CSSProperties} />;
}

export function StatusTag({ status, mono = true }: { status: TaskStatus; mono?: boolean }) {
  return (
    <span className="k-stag" style={{ '--c': STATUS[status].c } as React.CSSProperties}>
      <Dot status={status} live={status === 'RUNNING' || status === 'AWAITING_APPROVAL'} />
      <span className={mono ? 'mono' : ''}>{STATUS[status].label}</span>
    </span>
  );
}

/** Task.usedAgent — görevi fiilen çözen araç. */
export function UsedAgentMark({ a, s = 22, showName }: { a: UsedAgent; s?: number; showName?: boolean }) {
  const ag = USED_AGENT[a];
  return (
    <span className="k-ag">
      <span className="k-ag-mark mono" style={{ '--c': ag.c, width: s, height: s, fontSize: s <= 20 ? 9 : 10 } as React.CSSProperties}>
        {ag.mark}
      </span>
      {showName && <span className="k-ag-name">{ag.name}</span>}
    </span>
  );
}

/** AgentConnection.agentType — org'a bağlı bulut ajan hesabı (Claude/ChatGPT/Gemini). */
export function AgentTypeMark({ a, s = 22, showName }: { a: AgentType; s?: number; showName?: boolean }) {
  const ag = AGENT_TYPE[a];
  return (
    <span className="k-ag">
      <span className="k-ag-mark mono" style={{ '--c': ag.c, width: s, height: s, fontSize: s <= 20 ? 9 : 10 } as React.CSSProperties}>
        {ag.mark}
      </span>
      {showName && <span className="k-ag-name">{ag.name}</span>}
    </span>
  );
}

export function CapTag({ cap, on = true }: { cap: string; on?: boolean }) {
  return <span className={'k-cap mono' + (on ? '' : ' k-cap-off')}>{cap}</span>;
}

export function RiskTag({ risk }: { risk: string }) {
  return <span className={'k-risk mono k-risk-' + risk.toLowerCase()}>{risk}</span>;
}

export function RoleBadge({ role }: { role: Role }) {
  return <span className={'k-role mono k-role-' + role.toLowerCase()}>{role}</span>;
}

export function Meter({ v, c = 'var(--color-accent)', h = 3 }: { v: number; c?: string; h?: number }) {
  return (
    <span className="k-meter" style={{ height: h }}>
      <span style={{ width: Math.max(0, Math.min(100, v)) + '%', background: c }} />
    </span>
  );
}

export function Panel({
  title,
  kicker,
  right,
  children,
  pad = true,
  className = '',
  style,
}: {
  title?: ReactNode;
  kicker?: ReactNode;
  right?: ReactNode;
  children?: ReactNode;
  pad?: boolean;
  className?: string;
  style?: React.CSSProperties;
}) {
  return (
    <section className={'k-panel ' + className} style={style}>
      {(title || right) && (
        <header className="k-panel-h">
          <div className="k-panel-t">
            {kicker && <span className="k-kicker mono">{kicker}</span>}
            {title && <h5>{title}</h5>}
          </div>
          {right && <div className="k-panel-r">{right}</div>}
        </header>
      )}
      <div className={pad ? 'k-panel-b' : ''}>{children}</div>
    </section>
  );
}

export function Field({ label, hint, children }: { label: string; hint?: string; children: ReactNode }) {
  return (
    <label className="k-field">
      <span className="k-field-l mono">{label}</span>
      {children}
      {hint && <span className="k-field-h">{hint}</span>}
    </label>
  );
}

export function Btn({
  kind = 'sec',
  icon,
  children,
  className = '',
  ...rest
}: {
  kind?: 'pri' | 'sec' | 'dan' | 'ico';
  icon?: string;
  children?: ReactNode;
  className?: string;
} & React.ButtonHTMLAttributes<HTMLButtonElement>) {
  return (
    <button className={'k-btn k-btn-' + kind + ' ' + className} {...rest}>
      {icon && <Icon n={icon} s={14} />}
      {children && <span>{children}</span>}
    </button>
  );
}

export function Reveal({
  i = 0,
  children,
  className = '',
  tag = 'div',
  ...rest
}: {
  i?: number;
  children?: ReactNode;
  className?: string;
  tag?: keyof React.JSX.IntrinsicElements;
} & Record<string, unknown>) {
  return createElement(tag, { className: 'k-rv ' + className, style: { '--i': i }, ...rest }, children);
}
