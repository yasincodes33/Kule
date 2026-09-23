import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Icon } from '../../shared/components/Icon';
import { describeNotification } from '../../shared/domain/enums';
import { ago } from '../../shared/utils/format';
import { useMarkNotificationReadMutation, useNotificationsQuery, useUnreadCountQuery } from './hooks';
import type { NotificationResponse } from '../../shared/api/types';

export function NotificationBell() {
  const [open, setOpen] = useState(false);
  const notifications = useNotificationsQuery().data ?? [];
  const unread = useUnreadCountQuery().data ?? 0;
  const markRead = useMarkNotificationReadMutation();
  const navigate = useNavigate();
  const recent = notifications.slice(0, 8);

  const onClickItem = async (n: NotificationResponse) => {
    if (!n.read) markRead.mutate(n.id);
    const { link } = describeNotification(n.type, n.payload);
    setOpen(false);
    if (link) navigate(link);
  };

  return (
    <div style={{ position: 'relative' }}>
      <button className="k-btn k-btn-ico" onClick={() => setOpen((o) => !o)} title="Bildirimler" style={{ position: 'relative' }}>
        <Icon n="bell" s={16} />
        {unread > 0 && (
          <span className="mono k-nav-badge" style={{ position: 'absolute', top: -5, right: -5 }}>
            {unread > 9 ? '9+' : unread}
          </span>
        )}
      </button>
      {open && (
        <>
          <div style={{ position: 'fixed', inset: 0, zIndex: 29 }} onClick={() => setOpen(false)} />
          <div className="k-drop" style={{ right: 0, left: 'auto', width: 320, zIndex: 30 }}>
            {recent.length === 0 && <div className="k-drop-i mono k-sub">bildirim yok</div>}
            {recent.map((n) => {
              const d = describeNotification(n.type, n.payload);
              return (
                <button key={n.id} className="k-drop-i" style={{ opacity: n.read ? 0.55 : 1 }} onClick={() => onClickItem(n)}>
                  <span className="k-dot" style={{ '--c': d.c, width: 6, height: 6, marginTop: 4 } as React.CSSProperties} />
                  <span style={{ flex: 1, textAlign: 'left' }}>
                    <div style={{ fontSize: 11.5 }}>{d.text}</div>
                    <div className="mono k-sub">{ago(n.createdAt)}</div>
                  </span>
                </button>
              );
            })}
            <button className="k-drop-i mono k-sub" onClick={() => { setOpen(false); navigate('/notifications'); }}>
              tümünü gör →
            </button>
          </div>
        </>
      )}
    </div>
  );
}
