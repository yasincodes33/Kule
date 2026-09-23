import { useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import type { TaskResponse } from '../../shared/api/types';
import { Btn, CapTag, Field, StatusTag, UsedAgentMark } from '../../shared/components/primitives';
import { useToast } from '../../shared/components/Toasts';
import { ago } from '../../shared/utils/format';
import { useTaskDetailActionMutation, useTaskLogsQuery, useTaskQuery, useUpdateTaskPromptMutation } from './hooks';
import { useTaskLogStream } from './useTaskLogStream';
import { LogConsole, type LogLine } from './LogConsole';

function MetaRow({ l, children }: { l: string; children: React.ReactNode }) {
  return (
    <div className="k-meta-row">
      <span className="mono k-meta-l">{l}</span>
      <span className="k-meta-v mono">{children}</span>
    </div>
  );
}

export default function TaskDetailPage() {
  const { taskId = '' } = useParams();
  const navigate = useNavigate();
  const taskQuery = useTaskQuery(taskId);
  const logsQuery = useTaskLogsQuery(taskId);
  const { live, connected } = useTaskLogStream(taskId);
  const action = useTaskDetailActionMutation(taskId, taskQuery.data?.projectId);

  const logs: LogLine[] = useMemo(() => {
    const history = (logsQuery.data ?? []).map((l) => ({ id: l.id, level: l.level, message: l.message, timestamp: l.timestamp }));
    const seen = new Set(history.map((l) => l.id));
    const liveOnly = live.filter((l) => !seen.has(l.id)).map((l) => ({ id: l.id, level: l.level, message: l.message, timestamp: l.timestamp }));
    return [...history, ...liveOnly];
  }, [logsQuery.data, live]);

  if (taskQuery.isLoading || !taskQuery.data) {
    return <div className="k-page"><p className="k-sub">Görev yükleniyor…</p></div>;
  }

  const task = taskQuery.data;
  const canCancel = task.status === 'QUEUED' || task.status === 'DISPATCHED' || task.status === 'RUNNING';
  const canRetry = task.status === 'FAILED' || task.status === 'CANCELLED' || task.status === 'REJECTED';

  return (
    <div className="k-page">
      <div className="k-page-h">
        <div className="k-detail-h">
          <button className="k-back mono" onClick={() => navigate(-1)}>← GERİ</button>
          <div>
            <div className="k-detail-id">
              <span className="mono k-id lg">{task.id.slice(0, 8)}</span>
              <StatusTag status={task.status} />
              <CapTag cap={task.type} />
              {task.retryCount > 0 && <span className="mono k-sub">deneme #{task.retryCount}</span>}
            </div>
            <h3 className="k-detail-title">{task.title}</h3>
          </div>
        </div>
        <div className="k-page-actions">
          {task.status === 'AWAITING_APPROVAL' && <span className="mono k-gate">onay kuyruğunda bekliyor</span>}
          {canCancel && (
            <button className="k-btn k-btn-sec" disabled={action.isPending} onClick={() => action.mutate('cancel')}>
              İptal et
            </button>
          )}
          {canRetry && (
            <button className="k-btn k-btn-pri" disabled={action.isPending} onClick={() => action.mutate('retry')}>
              Yeniden dağıt
            </button>
          )}
        </div>
      </div>

      <div className="k-detail-grid">
        <div className="k-detail-main">
          <LogConsole taskId={task.id} logs={logs} connected={connected} />
        </div>
        <aside className="k-detail-side">
          <section className="k-panel">
            <header className="k-panel-h">
              <div className="k-panel-t">
                <span className="k-kicker mono">ATAMA</span>
                <h5>Yürütme bağlamı</h5>
              </div>
            </header>
            <div className="k-panel-b">
              <MetaRow l="ARAÇ">{task.usedAgent ? <UsedAgentMark a={task.usedAgent} s={20} showName /> : '—'}</MetaRow>
              <MetaRow l="RUNNER">{task.runnerConnectionId ? task.runnerConnectionId.slice(0, 8) : '—'}</MetaRow>
              <MetaRow l="ATANAN">{task.assignedUserId ? task.assignedUserId.slice(0, 8) : '—'}</MetaRow>
              <MetaRow l="OLUŞTURULDU">{ago(task.createdAt)}</MetaRow>
              <MetaRow l="GÜNCELLENDİ">{ago(task.updatedAt)}</MetaRow>
            </div>
          </section>

          {(task.status === 'DISPATCHED' || task.status === 'RUNNING') && <PromptPanel task={task} />}
        </aside>
      </div>
    </div>
  );
}

/**
 * Web artık yalnızca görev OLUŞTURMA + İZLEME (canlı+kalıcı log) için — hangi araçla çalıştırılıp
 * nasıl tamamlanacağı `agentsaas-desktop`'ın native görev paneline ya da runner.js CLI'sinin kendi
 * `readline` sorularına taşındı (bkz. README §E2/§F2, TaskOrchestrationService.completeTask hâlâ
 * her iki yolun da ortak backend işlemi). Burada yalnızca prompt görüntülenip düzenlenebiliyor.
 */
function PromptPanel({ task }: { task: TaskResponse }) {
  const [prompt, setPrompt] = useState(task.prompt ?? task.title);
  const updatePrompt = useUpdateTaskPromptMutation(task.id, task.projectId);
  const toast = useToast();
  const changed = prompt !== (task.prompt ?? task.title);

  const save = async () => {
    await updatePrompt.mutateAsync(prompt);
    toast('Prompt güncellendi', task.title, 'var(--color-accent)', 'check');
  };

  return (
    <section className="k-panel">
      <header className="k-panel-h">
        <div className="k-panel-t">
          <span className="k-kicker mono">YÜRÜTME</span>
          <h5>Prompt</h5>
        </div>
      </header>
      <div className="k-panel-b">
        <Field label="PROMPT" hint="modele/araca tam olarak bu metin gönderilir — masaüstü uygulaması/runner bunu okur">
          <textarea
            className="k-input mono"
            rows={4}
            value={prompt}
            onChange={(e) => setPrompt(e.target.value)}
          />
        </Field>
        <div className="k-form-actions">
          <Btn kind="pri" icon="check" disabled={!changed || updatePrompt.isPending} onClick={save}>Kaydet</Btn>
        </div>
      </div>
    </section>
  );
}
