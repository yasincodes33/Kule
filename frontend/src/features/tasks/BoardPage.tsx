import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ApiError } from '../../shared/api/client';
import { Btn, Field, Panel, Reveal } from '../../shared/components/primitives';
import { Icon } from '../../shared/components/Icon';
import { useToast } from '../../shared/components/Toasts';
import { MODEL_TIERS, STATUS, STATUS_ORDER, TASK_TYPES, type ModelTier, type TaskType } from '../../shared/domain/enums';
import { useAgentConnectionsQuery } from '../agents/hooks';
import { ProjectForm } from '../projects/ProjectForm';
import { useRunnersQuery } from '../runners/hooks';
import { useCreateTaskMutation, useProjectsQuery, useTasksQuery } from './hooks';
import { TaskCard } from './TaskCard';
import { TaskRow } from './TaskRow';

export default function BoardPage() {
  const projectsQuery = useProjectsQuery();
  const projects = projectsQuery.data ?? [];
  const [projectId, setProjectId] = useState<string | null>(null);

  useEffect(() => {
    if (!projectId && projects.length > 0) setProjectId(projects[0].id);
    if (projectId && !projects.some((p) => p.id === projectId)) setProjectId(projects[0]?.id ?? null);
  }, [projects, projectId]);

  if (projectsQuery.isLoading) return <div className="k-page"><p className="k-sub">Projeler yükleniyor…</p></div>;

  if (projects.length === 0) return <NoProjectsPanel />;

  return <BoardContent projectId={projectId} projects={projects} onSwitchProject={setProjectId} />;
}

function NoProjectsPanel() {
  return (
    <div className="k-page">
      <div className="k-page-h">
        <div>
          <span className="k-kicker mono">OPERASYON / GÖREV AKIŞI</span>
          <h3>Görev panosu</h3>
        </div>
      </div>
      <Panel kicker="İLK ADIM" title="Bu organizasyonda henüz proje yok" style={{ maxWidth: 420 }}>
        <ProjectForm onDone={() => {}} />
      </Panel>
    </div>
  );
}

function BoardContent({
  projectId,
  projects,
  onSwitchProject,
}: {
  projectId: string | null;
  projects: { id: string; name: string }[];
  onSwitchProject: (id: string) => void;
}) {
  const tasksQuery = useTasksQuery(projectId);
  const tasks = useMemo(() => tasksQuery.data ?? [], [tasksQuery.data]);
  const [view, setView] = useState<'board' | 'list'>('board');
  const [q, setQ] = useState('');
  const [kind, setKind] = useState<TaskType | 'all'>('all');
  const [modalOpen, setModalOpen] = useState(false);
  const navigate = useNavigate();

  const filtered = tasks.filter(
    (t) => (kind === 'all' || t.type === kind) && (q === '' || (t.title + t.id).toLowerCase().includes(q.toLowerCase())),
  );
  const counts = STATUS_ORDER.map((s) => [s, tasks.filter((t) => t.status === s).length] as const);

  return (
    <div className="k-page">
      <div className="k-page-h">
        <div>
          <span className="k-kicker mono">OPERASYON / GÖREV AKIŞI</span>
          <h3>Görev panosu</h3>
        </div>
        <div className="k-page-actions">
          {projects.length > 1 && (
            <select className="k-select mono" value={projectId ?? ''} onChange={(e) => onSwitchProject(e.target.value)}>
              {projects.map((p) => (
                <option key={p.id} value={p.id}>{p.name}</option>
              ))}
            </select>
          )}
          <button className="k-link mono" onClick={() => navigate('/projects')}>PROJELERİ YÖNET</button>
          <div className="k-seg2">
            <button className={view === 'board' ? 'on' : ''} onClick={() => setView('board')}><Icon n="board" s={14} /><span>Kanban</span></button>
            <button className={view === 'list' ? 'on' : ''} onClick={() => setView('list')}><Icon n="list" s={14} /><span>Liste</span></button>
          </div>
          <Btn kind="pri" icon="plus" onClick={() => setModalOpen(true)}>Görev dağıt</Btn>
        </div>
      </div>

      <div className="k-strip">
        {counts.map(([s, n], i) => (
          <Reveal key={s} i={i} className="k-strip-cell" style={{ '--c': STATUS[s].c } as React.CSSProperties}>
            <span className="mono k-strip-n">{String(n).padStart(2, '0')}</span>
            <span className="mono k-strip-l">{s}</span>
          </Reveal>
        ))}
      </div>

      <div className="k-filters">
        <div className="k-search">
          <Icon n="search" s={14} c="var(--ink-3)" />
          <input className="mono" value={q} onChange={(e) => setQ(e.target.value)} placeholder="görev veya ID ara…" />
        </div>
        <div className="k-chips">
          <button className={'k-chip mono' + (kind === 'all' ? ' on' : '')} onClick={() => setKind('all')}>HEPSİ</button>
          {TASK_TYPES.map((c) => (
            <button key={c} className={'k-chip mono' + (kind === c ? ' on' : '')} onClick={() => setKind(c)}>{c}</button>
          ))}
        </div>
      </div>

      {tasksQuery.isLoading ? (
        <p className="k-sub">Görevler yükleniyor…</p>
      ) : view === 'board' ? (
        <div className="k-lanes">
          {STATUS_ORDER.map((s, li) => {
            const items = filtered.filter((t) => t.status === s);
            return (
              <div key={s} className="k-lane" style={{ '--c': STATUS[s].c } as React.CSSProperties}>
                <div className="k-lane-h">
                  <span className="mono k-lane-l">{s}</span>
                  <span className="mono k-lane-n">{items.length}</span>
                </div>
                <div className="k-lane-b">
                  {items.map((t, i) => <TaskCard key={t.id} t={t} i={li + i} />)}
                  {items.length === 0 && <div className="k-lane-empty mono">boş</div>}
                </div>
              </div>
            );
          })}
        </div>
      ) : (
        <div className="k-tablewrap">
          <table className="k-table">
            <thead><tr>{['ID', 'DURUM', 'GÖREV', 'ARAÇ', 'TİP', 'RUNNER', 'DENEME', 'GÜNCELLEME'].map((h) => <th key={h} className="mono">{h}</th>)}</tr></thead>
            <tbody>{filtered.map((t, i) => <TaskRow key={t.id} t={t} i={i} />)}</tbody>
          </table>
        </div>
      )}

      {modalOpen && projectId && <CreateTaskModal projectId={projectId} onClose={() => setModalOpen(false)} />}
    </div>
  );
}

function CreateTaskModal({ projectId, onClose }: { projectId: string; onClose: () => void }) {
  const [type, setType] = useState<TaskType>('DEV');
  const [title, setTitle] = useState('');
  const [prompt, setPrompt] = useState('');
  const [advanced, setAdvanced] = useState(false);
  const [runnerConnectionId, setRunnerConnectionId] = useState('');
  const [agentConnectionId, setAgentConnectionId] = useState('');
  const [preferredModelTier, setPreferredModelTier] = useState<ModelTier | ''>('');
  const create = useCreateTaskMutation(projectId);
  const runners = useRunnersQuery().data ?? [];
  const agents = useAgentConnectionsQuery().data ?? [];
  const toast = useToast();
  const error = create.error instanceof ApiError ? create.error.message : create.error ? 'Görev oluşturulamadı' : null;

  const submit = async () => {
    if (title.trim().length === 0) return;
    const task = await create.mutateAsync({
      type,
      title: title.trim(),
      runnerConnectionId: runnerConnectionId || null,
      agentConnectionId: agentConnectionId || null,
      preferredModelTier: preferredModelTier || null,
      prompt: prompt.trim() || null,
    });
    toast('Görev dağıtıldı', task.id.slice(0, 8) + ' · kuyruğa alındı', 'var(--st-ok)', 'check');
    onClose();
  };

  return (
    <div style={{ position: 'fixed', inset: 0, display: 'grid', placeItems: 'center', background: 'rgba(0,0,0,.55)', zIndex: 50 }} onClick={onClose}>
      <div style={{ width: 'min(420px, 90vw)' }} onClick={(e) => e.stopPropagation()}>
        <Panel kicker="YENİ GÖREV" title="Görev dağıt" right={<button className="k-link mono" onClick={onClose}>KAPAT</button>}>
          {error && <p className="k-sub k-err" style={{ marginBottom: 'var(--space-3)' }}>{error}</p>}
          <Field label="TİP">
            <div className="k-seg2 full">
              {TASK_TYPES.map((c) => (
                <button key={c} className={type === c ? 'on' : ''} onClick={() => setType(c)}><span className="mono">{c}</span></button>
              ))}
            </div>
          </Field>
          <Field label="BAŞLIK">
            <input className="k-input mono" value={title} onChange={(e) => setTitle(e.target.value)} placeholder="Faturalama servisinde yuvarlama hatasını düzelt" autoFocus />
          </Field>
          <button className="k-link mono" style={{ display: 'block', marginBottom: 'var(--space-3)' }} onClick={() => setAdvanced((v) => !v)}>
            {advanced ? '− gelişmiş seçenekleri gizle' : '+ gelişmiş seçenekler (runner / ajan / model)'}
          </button>
          {advanced && (
            <>
              <Field label="PROMPT" hint="boş bırakılırsa modele/araca gönderilirken başlık kullanılır">
                <textarea
                  className="k-input mono"
                  rows={3}
                  value={prompt}
                  onChange={(e) => setPrompt(e.target.value)}
                  placeholder="Modele/araca tam olarak ne yapması gerektiğini anlatan ayrıntılı talimat"
                />
              </Field>
              <Field label="RUNNER" hint="boş bırakılırsa otomatik seçilir">
                <select className="k-select mono" style={{ width: '100%' }} value={runnerConnectionId} onChange={(e) => setRunnerConnectionId(e.target.value)}>
                  <option value="">— otomatik —</option>
                  {runners.map((r) => <option key={r.id} value={r.id}>{r.label}</option>)}
                </select>
              </Field>
              <Field label="BULUT AJANI" hint="belirli bir Claude/ChatGPT/Gemini bağlantısına zorlar">
                <select className="k-select mono" style={{ width: '100%' }} value={agentConnectionId} onChange={(e) => setAgentConnectionId(e.target.value)}>
                  <option value="">— otomatik —</option>
                  {agents.map((a) => <option key={a.id} value={a.id}>{a.agentType} · {a.id.slice(0, 8)}</option>)}
                </select>
              </Field>
              <Field label="MODEL SEVİYESİ">
                <div className="k-seg2 full">
                  {MODEL_TIERS.map((m) => (
                    <button key={m} className={preferredModelTier === m ? 'on' : ''} onClick={() => setPreferredModelTier(preferredModelTier === m ? '' : m)}>
                      <span className="mono">{m}</span>
                    </button>
                  ))}
                </div>
              </Field>
            </>
          )}
          <Btn kind="pri" icon="arrow" className="k-btn-block" disabled={title.trim().length === 0 || create.isPending} onClick={submit}>
            {create.isPending ? 'DAĞITILIYOR…' : 'Kuyruğa al'}
          </Btn>
        </Panel>
      </div>
    </div>
  );
}
