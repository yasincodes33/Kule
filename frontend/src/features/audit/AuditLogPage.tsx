import { Panel, Reveal } from '../../shared/components/primitives';
import { ago } from '../../shared/utils/format';
import { useAuditLogQuery } from './hooks';

export default function AuditLogPage() {
  const auditQuery = useAuditLogQuery();
  const entries = auditQuery.data ?? [];

  return (
    <div className="k-page">
      <div className="k-page-h">
        <div>
          <span className="k-kicker mono">DENETİM / GEÇMİŞ</span>
          <h3>Denetim kaydı</h3>
        </div>
        <div className="k-page-actions"><span className="mono k-sub">{entries.length} olay</span></div>
      </div>

      {auditQuery.isLoading ? (
        <p className="k-sub">Yükleniyor…</p>
      ) : entries.length === 0 ? (
        <Panel kicker="TEMİZ" title="Henüz denetim kaydı yok">
          <p className="k-sub">Üye davetleri, sahiplik devri, görev/onay olayları gibi organizasyon içi işlemler burada listelenir.</p>
        </Panel>
      ) : (
        <div className="k-tablewrap">
          <table className="k-table">
            <thead><tr>{['OLAY', 'HEDEF', 'AKTÖR', 'ZAMAN'].map((h) => <th key={h} className="mono">{h}</th>)}</tr></thead>
            <tbody>
              {entries.map((e, i) => (
                <Reveal key={e.id} i={i} tag="tr">
                  <td className="mono">{e.action.replaceAll('_', ' ')}</td>
                  <td className="mono k-sub">{e.entityType}{e.entityId ? ' · ' + e.entityId.slice(0, 8) : ''}</td>
                  <td className="mono k-sub">{e.actorUserId ? e.actorUserId.slice(0, 8) : 'sistem'}</td>
                  <td className="mono k-sub k-num">{ago(e.createdAt)}</td>
                </Reveal>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
