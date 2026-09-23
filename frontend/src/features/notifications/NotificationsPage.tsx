import { useNavigate } from 'react-router-dom';
import { Panel, Reveal } from '../../shared/components/primitives';
import { describeNotification } from '../../shared/domain/enums';
import { ago } from '../../shared/utils/format';
import { useMarkNotificationReadMutation, useNotificationsQuery } from './hooks';

export default function NotificationsPage() {
  const notificationsQuery = useNotificationsQuery();
  const notifications = notificationsQuery.data ?? [];
  const markRead = useMarkNotificationReadMutation();
  const navigate = useNavigate();

  return (
    <div className="k-page">
      <div className="k-page-h">
        <div>
          <span className="k-kicker mono">HESAP</span>
          <h3>Bildirimler</h3>
        </div>
      </div>

      {notificationsQuery.isLoading ? (
        <p className="k-sub">Yükleniyor…</p>
      ) : notifications.length === 0 ? (
        <Panel kicker="TEMİZ" title="Hiç bildirim yok">
          <p className="k-sub">Onay talepleri, görev tamamlanma/başarısızlık olayları ve organizasyon davetleri burada görünür.</p>
        </Panel>
      ) : (
        <div className="k-tablewrap">
          <table className="k-table">
            <thead><tr>{['', 'BİLDİRİM', 'ZAMAN', ''].map((h) => <th key={h} className="mono">{h}</th>)}</tr></thead>
            <tbody>
              {notifications.map((n, i) => {
                const d = describeNotification(n.type, n.payload);
                return (
                  <Reveal key={n.id} i={i} tag="tr" style={{ opacity: n.read ? 0.6 : 1, cursor: d.link ? 'pointer' : 'default' }} onClick={() => d.link && navigate(d.link)}>
                    <td><span className="k-dot" style={{ '--c': d.c } as React.CSSProperties} /></td>
                    <td>{d.text}</td>
                    <td className="mono k-sub">{ago(n.createdAt)}</td>
                    <td className="k-td-r">
                      {!n.read && (
                        <button className="k-link mono" onClick={(e) => { e.stopPropagation(); markRead.mutate(n.id); }}>
                          OKUNDU İŞARETLE
                        </button>
                      )}
                    </td>
                  </Reveal>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
