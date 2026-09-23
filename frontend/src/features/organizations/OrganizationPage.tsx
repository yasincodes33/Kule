import { useState } from 'react';
import { ApiError } from '../../shared/api/client';
import { useAuthStore } from '../../shared/auth/authStore';
import { Btn, Field, Panel, Reveal, RoleBadge } from '../../shared/components/primitives';
import { useToast } from '../../shared/components/Toasts';
import { ROLES, ROLE_ORDER, type Role } from '../../shared/domain/enums';
import { InvitationsPanel } from './InvitationsPanel';
import {
  useInviteMemberMutation,
  useMembersQuery,
  useOrganizationsQuery,
  useRevokeMemberMutation,
  useTransferOwnershipMutation,
} from './hooks';
import type { MembershipResponse } from '../../shared/api/types';

const INVITABLE_ROLES = ROLE_ORDER.filter((r) => r !== 'OWNER');

export default function OrganizationPage() {
  const { organizations, activeOrgId, setActiveOrgId } = useAuthStore();
  useOrganizationsQuery();
  const cur = organizations.find((o) => o.id === activeOrgId);

  return (
    <div className="k-page">
      <div className="k-page-h">
        <div>
          <span className="k-kicker mono">KİRACI / ERİŞİM</span>
          <h3>Organizasyon &amp; üyeler</h3>
        </div>
        {cur && (
          <div className="k-page-actions">
            <span className="mono k-sub">{cur.slug}</span>
            <span className="mono k-sub" style={{ marginLeft: 'var(--space-3)' }}>ID: {cur.id}</span>
          </div>
        )}
      </div>

      <InvitationsPanel />

      <div className="k-org-switch">
        {organizations.map((o, i) => (
          <Reveal key={o.id} i={i} tag="button" className={'k-org' + (o.id === activeOrgId ? ' on' : '')} onClick={() => setActiveOrgId(o.id)}>
            <div className="k-org-h">
              <span className="k-org-mark mono">{o.name.slice(0, 2).toUpperCase()}</span>
              <div className="k-org-t">
                <span className="k-org-name">{o.name}</span>
                <span className="mono k-sub">{o.slug} · {o.id}</span>
              </div>
              {o.id === activeOrgId && <span className="mono k-org-on">AKTİF</span>}
            </div>
          </Reveal>
        ))}
      </div>

      {activeOrgId && <MembersSection organizationId={activeOrgId} />}
    </div>
  );
}

function MembersSection({ organizationId }: { organizationId: string }) {
  const membersQuery = useMembersQuery(organizationId);
  const members = membersQuery.data ?? [];
  const currentUserId = useAuthStore((s) => s.user?.id);
  const myMembership = members.find((m) => m.userId === currentUserId);
  const iAmOwner = myMembership?.role === 'OWNER';
  const active = members.filter((m) => m.status === 'ACTIVE');
  const invited = members.filter((m) => m.status === 'PENDING');
  const revoke = useRevokeMemberMutation(organizationId);
  const transfer = useTransferOwnershipMutation(organizationId);
  const toast = useToast();

  return (
    <div className="k-org-grid">
      <Panel kicker={'ÜYELER · ' + active.length + ' AKTİF / ' + members.length} title="Erişim listesi" pad={false}>
        <div className="k-tablewrap flat">
          <table className="k-table">
            <thead><tr>{['ÜYE', 'ROL', 'DURUM', ''].map((h) => <th key={h} className="mono">{h}</th>)}</tr></thead>
            <tbody>
              {members.map((m: MembershipResponse, i: number) => {
                const isSelf = m.userId != null && m.userId === currentUserId;
                return (
                  <Reveal key={m.id} i={i} tag="tr">
                    <td>
                      <div className="k-member">
                        <span className="k-org-mark mono sm">{(m.invitedEmail ?? m.userId ?? '??').slice(0, 2).toUpperCase()}</span>
                        <span className="mono">
                          {m.userId ? m.userId.slice(0, 8) : m.invitedEmail}
                          {m.status === 'PENDING' && <span className="mono k-inv"> · DAVETLİ</span>}
                        </span>
                      </div>
                    </td>
                    <td><RoleBadge role={m.role} /></td>
                    <td className="mono k-sub">{m.status}</td>
                    <td className="k-td-r">
                      {m.status === 'ACTIVE' && m.role !== 'OWNER' && (
                        <>
                          {iAmOwner && !isSelf && (
                            <button
                              className="k-link mono"
                              style={{ marginRight: 'var(--space-3)' }}
                              onClick={async () => {
                                await transfer.mutateAsync(m.userId as string);
                                toast('Sahiplik devredildi', m.userId?.slice(0, 8), 'var(--color-accent)', 'layers');
                              }}
                            >
                              SAHİPLİĞİ DEVRET
                            </button>
                          )}
                          <button
                            className="k-link mono"
                            onClick={async () => {
                              await revoke.mutateAsync(m.id);
                              toast('Üyelik kaldırıldı', m.userId?.slice(0, 8) ?? m.invitedEmail ?? '', 'var(--st-cancel)', 'x');
                            }}
                          >
                            KALDIR
                          </button>
                        </>
                      )}
                      {m.status === 'PENDING' && (
                        <button
                          className="k-link mono"
                          onClick={async () => {
                            await revoke.mutateAsync(m.id);
                            toast('Davet iptal edildi', m.invitedEmail ?? '', 'var(--st-cancel)', 'x');
                          }}
                        >
                          DAVETİ İPTAL ET
                        </button>
                      )}
                    </td>
                  </Reveal>
                );
              })}
              {members.length === 0 && !membersQuery.isLoading && (
                <tr><td colSpan={4} className="mono k-sub">henüz üye yok</td></tr>
              )}
            </tbody>
          </table>
        </div>
      </Panel>

      <div className="k-org-side">
        <InvitePanel organizationId={organizationId} pendingCount={invited.length} />
        <Panel kicker="POLİTİKA" title="Onay zinciri">
          <div className="k-meta-row"><span className="mono k-meta-l">PROD YAZMA</span><span className="k-meta-v mono">APPROVER + üstü</span></div>
          <div className="k-meta-row"><span className="mono k-meta-l">SELF-APPROVE</span><span className="k-meta-v mono">kapalı</span></div>
        </Panel>
      </div>
    </div>
  );
}

function InvitePanel({ organizationId, pendingCount }: { organizationId: string; pendingCount: number }) {
  const [email, setEmail] = useState('');
  const [role, setRole] = useState<Role>('DEVELOPER');
  const invite = useInviteMemberMutation(organizationId);
  const toast = useToast();
  const error = invite.error instanceof ApiError ? invite.error.message : invite.error ? 'Davet gönderilemedi' : null;
  const valid = /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email);

  const submit = async () => {
    await invite.mutateAsync({ email, role });
    toast('Davet gönderildi', email + ' · ' + role, 'var(--color-accent)', 'mail');
    setEmail('');
  };

  return (
    <Panel kicker={'DAVET' + (pendingCount ? ' · ' + pendingCount + ' BEKLEYEN' : '')} title="Üye davet et">
      {error && <p className="k-sub k-err" style={{ marginBottom: 'var(--space-3)' }}>{error}</p>}
      <Field label="E-POSTA">
        <input className="k-input mono" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="isim@sirket.com" />
      </Field>
      <div className="k-role-pick">
        <span className="mono k-field-l">ROL</span>
        {INVITABLE_ROLES.map((r) => (
          <button key={r} className={'k-role-opt' + (role === r ? ' on' : '')} onClick={() => setRole(r)}>
            <RoleBadge role={r} />
            <span className="k-role-note">{ROLES[r].note}</span>
          </button>
        ))}
      </div>
      <Btn kind="pri" icon="mail" disabled={!valid || invite.isPending} className="k-btn-block" onClick={submit}>Daveti gönder</Btn>
    </Panel>
  );
}
