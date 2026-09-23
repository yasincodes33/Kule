import { useNavigate } from 'react-router-dom';
import { STATUS } from '../../shared/domain/enums';
import { CapTag, Reveal, UsedAgentMark } from '../../shared/components/primitives';
import { ago } from '../../shared/utils/format';
import type { TaskResponse } from '../../shared/api/types';

export function TaskCard({ t, i }: { t: TaskResponse; i: number }) {
  const st = STATUS[t.status];
  const navigate = useNavigate();
  return (
    <Reveal
      i={i}
      className="k-card"
      style={{ '--c': st.c }}
      tag="article"
      role="button"
      tabIndex={0}
      onClick={() => navigate(`/tasks/${t.id}`)}
      onKeyDown={(e: React.KeyboardEvent) => e.key === 'Enter' && navigate(`/tasks/${t.id}`)}
    >
      <div className="k-card-top">
        <span className="mono k-id">{t.id.slice(0, 8)}</span>
        {t.retryCount > 0 && <span className="mono k-prio k-prio-P1">RETRY ×{t.retryCount}</span>}
      </div>
      <h6 className="k-card-title">{t.title}</h6>
      <div className="k-card-mid">
        {t.usedAgent ? <UsedAgentMark a={t.usedAgent} s={20} /> : <span className="mono k-sub">atanmamış</span>}
        <CapTag cap={t.type} />
      </div>
      <div className="k-card-foot">
        <span className="mono k-sub">{ago(t.updatedAt)}</span>
        {t.status === 'AWAITING_APPROVAL' && <span className="mono k-gate">ONAY →</span>}
        {(t.status === 'FAILED' || t.status === 'REJECTED') && <span className="mono k-sub k-err">exit ≠ 0</span>}
      </div>
    </Reveal>
  );
}
