import { useNavigate } from 'react-router-dom';
import { CapTag, Reveal, StatusTag, UsedAgentMark } from '../../shared/components/primitives';
import { ago } from '../../shared/utils/format';
import type { TaskResponse } from '../../shared/api/types';

export function TaskRow({ t, i }: { t: TaskResponse; i: number }) {
  const navigate = useNavigate();
  return (
    <Reveal i={i} tag="tr" style={{ cursor: 'pointer' }} onClick={() => navigate(`/tasks/${t.id}`)}>
      <td className="mono k-id">{t.id.slice(0, 8)}</td>
      <td><StatusTag status={t.status} /></td>
      <td className="k-row-title">{t.title}</td>
      <td>{t.usedAgent ? <UsedAgentMark a={t.usedAgent} s={20} showName /> : <span className="mono k-sub">—</span>}</td>
      <td><CapTag cap={t.type} /></td>
      <td className="mono k-sub">{t.runnerConnectionId ? t.runnerConnectionId.slice(0, 8) : '—'}</td>
      <td className="mono k-sub k-num">{t.retryCount}</td>
      <td className="mono k-sub k-num">{ago(t.updatedAt)}</td>
    </Reveal>
  );
}
