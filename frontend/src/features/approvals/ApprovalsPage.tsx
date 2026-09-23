import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ApiError } from '../../shared/api/client';
import { Btn, CapTag, Field, Panel, Reveal, StatusTag, UsedAgentMark } from '../../shared/components/primitives';
import { useToast } from '../../shared/components/Toasts';
import { ago, until } from '../../shared/utils/format';
import { useTaskQuery } from '../tasks/hooks';
import { useApprovalsQuery, useDecideApprovalMutation } from './hooks';
import type { ApprovalResponse } from '../../shared/api/types';

/**
 * `requestedBy` NULL olabiliyor: onay, bir web kullanıcısının değil, atanmamış bir GÖREVİN
 * araç çağrısından doğduğunda backend buraya null yazar. Doğrudan `.slice()`
 * çağrılıyordu ve sayfa bu durumda TypeError ile çöküp bomboş (siyah) bir ekran bırakıyordu.
 */
function who(userId: string | null) {
  return userId ? userId.slice(0, 8) : 'sistem (görev)';
}

export default function ApprovalsPage() {
  const approvalsQuery = useApprovalsQuery();
  const pending = approvalsQuery.data ?? [];
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const cur = pending.find((a) => a.id === selectedId) ?? pending[0];

  useEffect(() => {
    if (!cur && pending[0]) setSelectedId(pending[0].id);
  }, [pending, cur]);

  return (
    <div className="k-page">
      <div className="k-page-h">
        <div>
          <span className="k-kicker mono">İNSAN KAPISI / POLİTİKA</span>
          <h3>Onay kuyruğu</h3>
        </div>
        <div className="k-page-actions">
          <span className="mono k-sub">{pending.length} bekleyen</span>
        </div>
      </div>

      {approvalsQuery.isLoading ? (
        <p className="k-sub">Yükleniyor…</p>
      ) : approvalsQuery.isError ? (
        // Yukleme hatasini "onay yok" gibi gostermek tehlikeli: dolu bir kuyruk bos sanilabilir.
        <Panel kicker="HATA" title="Onay kuyrugu yuklenemedi">
          <p className="k-sub k-err">
            {approvalsQuery.error instanceof ApiError ? approvalsQuery.error.message : 'Sunucuya ulasilamadi'}
          </p>
          <button className="k-link mono" style={{ marginTop: 'var(--space-3)' }} onClick={() => approvalsQuery.refetch()}>
            tekrar dene &rarr;
          </button>
        </Panel>
      ) : pending.length === 0 ? (
        <Panel kicker="TEMİZ" title="Bekleyen onay yok">
          <p className="k-sub">Riskli bir araç çağrısı politikayı tetiklediğinde burada görünür ve ilgili görev bloke edilir.</p>
        </Panel>
      ) : (
        <div className="k-appr-grid">
          <div className="k-appr-list">
            {pending.map((a, i) => (
              <Reveal key={a.id} i={i} className={'k-appr-item' + (cur?.id === a.id ? ' on' : '')} tag="button" onClick={() => setSelectedId(a.id)}>
                <div className="k-appr-item-top">
                  <span className="mono k-id">{a.taskId.slice(0, 8)}</span>
                  {a.toolName && <span className="mono k-appr-tool">{a.toolName}</span>}
                </div>
                <div className="k-appr-item-foot">
                  <span className="mono k-sub">talep: {who(a.requestedBy)}</span>
                  <span className="mono k-sub">{ago(a.createdAt)}</span>
                </div>
              </Reveal>
            ))}
          </div>
          {cur && <ApprovalDetail key={cur.id} approval={cur} />}
        </div>
      )}
    </div>
  );
}

function ApprovalDetail({ approval }: { approval: ApprovalResponse }) {
  const taskQuery = useTaskQuery(approval.taskId);
  const [note, setNote] = useState('');
  const decide = useDecideApprovalMutation();
  const toast = useToast();
  const navigate = useNavigate();
  const error = decide.error instanceof ApiError ? decide.error.message : decide.error ? 'İşlem başarısız' : null;

  const submit = async (decision: 'approve' | 'reject') => {
    await decide.mutateAsync({ taskId: approval.taskId, approvalId: approval.id, decision, reason: note });
    toast(
      decision === 'approve' ? 'Onaylandı' : 'Reddedildi',
      approval.taskId.slice(0, 8),
      decision === 'approve' ? 'var(--st-ok)' : 'var(--st-fail)',
      decision === 'approve' ? 'check' : 'x',
    );
  };

  return (
    <div className="k-appr-detail">
      <Panel kicker="GÖREV" title={taskQuery.data?.title ?? approval.taskId.slice(0, 8)}>
        {taskQuery.data && (
          <div className="k-card-mid" style={{ marginBottom: 'var(--space-3)' }}>
            <StatusTag status={taskQuery.data.status} />
            <CapTag cap={taskQuery.data.type} />
            {taskQuery.data.usedAgent && <UsedAgentMark a={taskQuery.data.usedAgent} s={20} showName />}
          </div>
        )}
        <div className="k-meta-row"><span className="mono k-meta-l">TALEP EDEN</span><span className="k-meta-v mono">{who(approval.requestedBy)}</span></div>
        <div className="k-meta-row"><span className="mono k-meta-l">OLUŞTURULDU</span><span className="k-meta-v mono">{ago(approval.createdAt)}</span></div>
        <div className="k-meta-row"><span className="mono k-meta-l">SÜRE DOLUYOR</span><span className="k-meta-v mono">{until(approval.expiresAt)}</span></div>
        <button className="k-link mono" style={{ marginTop: 'var(--space-3)' }} onClick={() => navigate(`/tasks/${approval.taskId}`)}>
          görev loglarını aç →
        </button>
      </Panel>

      {/* Onaylayan kisi neyi onayladigini gormeden karar veremez: arac adi ve argumanlari
          burada gosteriliyor (bkz. V23 migration). Eski kayitlarda ve elle istenen
          onaylarda bu bilgi yok. */}
      <Panel kicker="ONAYLANACAK İŞLEM" title={approval.toolName ?? 'Araç bilgisi yok'}>
        {approval.toolName ? (
          <>
            <p className="k-sub" style={{ marginBottom: 'var(--space-3)' }}>
              Görevi çalıştıran ajan bu aracı çalıştırmak için izin istiyor.
            </p>
            {approval.toolArguments && (
              <pre className="mono k-appr-args">{approval.toolArguments}</pre>
            )}
          </>
        ) : (
          <p className="k-sub">
            Bu onay bir araç çağrısından doğmadı ya da araç bilgisi kaydedilmeden önce
            oluşturuldu. Ayrıntı için görev loglarına bakabilirsin.
          </p>
        )}
      </Panel>

      <Panel kicker="KARAR" title="Onay ver veya reddet">
        {error && <p className="k-sub k-err" style={{ marginBottom: 'var(--space-3)' }}>{error}</p>}
        <Field label="KARAR NOTU (REDDEDERKEN ÖNERİLİR)">
          <textarea className="k-input mono" rows={2} value={note} onChange={(e) => setNote(e.target.value)} placeholder="ör. bakım penceresi dışında" />
        </Field>
        <div className="k-appr-actions">
          <Btn kind="pri" icon="check" disabled={decide.isPending} onClick={() => submit('approve')}>Onayla ve sürdür</Btn>
          <Btn kind="dan" icon="x" disabled={decide.isPending} onClick={() => submit('reject')}>Reddet</Btn>
          <span className="mono k-sub">Kararın tamamı denetim kaydına yazılır.</span>
        </div>
      </Panel>
    </div>
  );
}
