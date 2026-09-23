import { Btn, Panel, Reveal, RoleBadge } from '../../shared/components/primitives';
import { useToast } from '../../shared/components/Toasts';
import { useAcceptInvitationMutation, usePendingInvitationsQuery, useRejectInvitationMutation } from './hooks';

/**
 * Backend'de `/me/invitations` + kabul/red uçları vardı ama frontend'de hiç
 * kullanılmıyordu — bir kullanıcı davet edilebiliyordu ama daveti kabul edeceği bir ekran yoktu.
 * Hem onboarding'de (CreateOrgPage — hiç organizasyonu olmayan kullanıcı) hem de organizasyon
 * sayfasında (OrganizationPage — zaten bir organizasyonu olan kullanıcı yeni bir davet aldığında)
 * kullanılmak üzere paylaşılan tek bir panel. `onAccepted` yalnızca onboarding'de anlamlı —
 * kabul sonrası artık organizasyonu olan kullanıcıyı uygulamaya yönlendirmek için; OrganizationPage
 * zaten uygulama içinde olduğundan bu prop'u vermeden kullanır (yönlendirme yapılmaz).
 */
export function InvitationsPanel({ onAccepted }: { onAccepted?: () => void }) {
  const invitationsQuery = usePendingInvitationsQuery();
  const accept = useAcceptInvitationMutation();
  const reject = useRejectInvitationMutation();
  const toast = useToast();

  const invitations = invitationsQuery.data ?? [];
  if (invitationsQuery.isLoading || invitations.length === 0) return null;

  return (
    <Panel kicker={'DAVETLER · ' + invitations.length + ' BEKLEYEN'} title="Bekleyen davetleriniz">
      {invitations.map((inv, i) => (
        <Reveal key={inv.membershipId} i={i} tag="div" className="k-meta-row">
          <span className="mono k-meta-l">
            {inv.organizationName ?? inv.organizationId.slice(0, 8)}
            <span style={{ marginLeft: 'var(--space-2)' }}><RoleBadge role={inv.role} /></span>
          </span>
          <span className="k-meta-v" style={{ display: 'flex', gap: 'var(--space-2)' }}>
            <Btn
              kind="pri"
              disabled={accept.isPending || reject.isPending}
              onClick={async () => {
                await accept.mutateAsync(inv.membershipId);
                toast('Davet kabul edildi', inv.organizationName ?? '', 'var(--color-accent)', 'check');
                onAccepted?.();
              }}
            >
              KABUL ET
            </Btn>
            <button
              className="k-link mono"
              disabled={accept.isPending || reject.isPending}
              onClick={async () => {
                await reject.mutateAsync(inv.membershipId);
                toast('Davet reddedildi', inv.organizationName ?? '', 'var(--st-cancel)', 'x');
              }}
            >
              REDDET
            </button>
          </span>
        </Reveal>
      ))}
    </Panel>
  );
}
