import { useState } from 'react';
import { ApiError } from '../../shared/api/client';
import { Btn, CapTag, Field, Panel, Reveal } from '../../shared/components/primitives';
import { useToast } from '../../shared/components/Toasts';
import { TASK_TYPES, type TaskType } from '../../shared/domain/enums';
import { ago } from '../../shared/utils/format';
import { AgentsPanel } from '../agents/AgentsPanel';
import { useAddRunnerCapabilityMutation, useIssueBridgeTokenMutation, useRegisterRunnerMutation, useRemoveRunnerMutation, useRunnersQuery } from './hooks';
import { TerminalAccessFlow } from './TerminalAccessFlow';
import { useDecideTerminalSessionMutation, usePendingTerminalSessionsQuery } from './terminalHooks';
import type { RunnerConnectionResponse, RunnerTerminalSessionResponse } from '../../shared/api/types';

export default function RunnersPage() {
  const [tab, setTab] = useState<'runners' | 'agents'>('runners');

  return (
    <div className="k-page">
      <div className="k-page-h">
        <div>
          <span className="k-kicker mono">FİLO / BAĞLANTILAR</span>
          <h3>Runner &amp; ajan bağlantıları</h3>
        </div>
        <div className="k-seg2">
          <button className={tab === 'runners' ? 'on' : ''} onClick={() => setTab('runners')}><span>Runner'lar</span></button>
          <button className={tab === 'agents' ? 'on' : ''} onClick={() => setTab('agents')}><span>Ajan bağlantıları</span></button>
        </div>
      </div>

      {tab === 'runners' ? <RunnerFleet /> : <AgentsPanel />}
    </div>
  );
}

function RunnerFleet() {
  const runnersQuery = useRunnersQuery();
  const runners = runnersQuery.data ?? [];
  const [open, setOpen] = useState(false);
  const online = runners.filter((r) => r.status === 'ONLINE').length;

  return (
    <>
      <div className="k-page-actions" style={{ justifyContent: 'space-between' }}>
        <span className="mono k-sub">{online}/{runners.length} online</span>
        <Btn kind="pri" icon="plus" onClick={() => setOpen(!open)}>Bağlantı ekle</Btn>
      </div>

      {open && <RegisterRunnerForm onDone={() => setOpen(false)} />}

      <PendingTerminalRequestsPanel />

      {runnersQuery.isLoading ? (
        <p className="k-sub">Yükleniyor…</p>
      ) : runners.length === 0 ? (
        <Panel kicker="FİLO BOŞ" title="Henüz bağlı runner yok">
          <p className="k-sub">Görevlerin çalışacağı bir yürütme ortamı bağlamak için yukarıdaki "Bağlantı ekle" düğmesini kullan.</p>
        </Panel>
      ) : (
        <div className="k-runner-grid">
          {runners.map((r, i) => <RunnerCard key={r.id} r={r} i={i} />)}
        </div>
      )}
    </>
  );
}

function RegisterRunnerForm({ onDone }: { onDone: () => void }) {
  const [label, setLabel] = useState('');
  const [caps, setCaps] = useState<TaskType[]>(['DEV']);
  const register = useRegisterRunnerMutation();
  const toast = useToast();
  const error = register.error instanceof ApiError ? register.error.message : register.error ? 'Runner kaydedilemedi' : null;
  const toggleCap = (c: TaskType) => setCaps((cs) => (cs.includes(c) ? cs.filter((x) => x !== c) : [...cs, c]));
  const valid = label.trim().length > 0 && caps.length > 0;

  const submit = async () => {
    const runner = await register.mutateAsync({ label: label.trim(), capabilities: caps });
    toast('Runner kaydedildi', runner.label + ' · bridge token oluşturmayı unutma', 'var(--st-ok)', 'server');
    onDone();
  };

  return (
    <Panel kicker="YENİ BAĞLANTI" title="Runner kaydet" className="k-rv">
      {error && <p className="k-sub k-err" style={{ marginBottom: 'var(--space-3)' }}>{error}</p>}
      <div className="k-form-grid">
        <Field label="RUNNER ADI" hint="filo içinde tekil">
          <input className="k-input mono" value={label} onChange={(e) => setLabel(e.target.value)} placeholder="atlas-03" />
        </Field>
        <Field label="YETENEKLER">
          <div className="k-chips">
            {TASK_TYPES.map((c) => (
              <button key={c} className={'k-chip mono' + (caps.includes(c) ? ' on' : '')} onClick={() => toggleCap(c)}>{c}</button>
            ))}
          </div>
        </Field>
        <div className="k-form-actions">
          <Btn kind="pri" icon="check" disabled={!valid || register.isPending} onClick={submit}>Kaydet</Btn>
          <Btn onClick={onDone}>Vazgeç</Btn>
        </div>
      </div>
    </Panel>
  );
}

function RunnerCard({ r, i }: { r: RunnerConnectionResponse; i: number }) {
  const [token, setToken] = useState<string | null>(null);
  const issueToken = useIssueBridgeTokenMutation();
  const addCap = useAddRunnerCapabilityMutation();
  const remove = useRemoveRunnerMutation();
  const toast = useToast();

  const getToken = async () => {
    const res = await issueToken.mutateAsync(r.id);
    setToken(res.token);
  };

  return (
    <Reveal i={i} className={'k-runner k-runner-' + r.status.toLowerCase()}>
      <div className="k-runner-h">
        <div className="k-runner-id">
          <span className={'k-pulse' + (r.status === 'ONLINE' ? '' : ' k-pulse-offline')} />
          <span className="mono k-runner-name">{r.label}</span>
        </div>
        <span className={'mono k-runner-state' + (r.status === 'ONLINE' ? ' k-rs-online' : '')}>{r.status}</span>
      </div>
      <div className="mono k-runner-host">{r.id.slice(0, 8)}</div>
      <div className="k-runner-caps">
        {TASK_TYPES.map((c) =>
          r.capabilities.includes(c) ? (
            <CapTag key={c} cap={c} on />
          ) : (
            <button
              key={c}
              className="k-cap mono k-cap-off"
              style={{ cursor: 'pointer' }}
              disabled={addCap.isPending}
              title={`${c} yeteneği ekle`}
              onClick={() => addCap.mutate({ runnerId: r.id, taskType: c })}
            >
              +{c}
            </button>
          ),
        )}
      </div>
      {token ? (
        <div>
          <div className="k-cmd mono" style={{ wordBreak: 'break-all' }}>
            <span>{token}</span>
          </div>
          <span className="mono k-sub k-err">yalnızca bir kez gösterilir — şimdi kopyala</span>
        </div>
      ) : (
        <span className="mono k-sub">{r.lastHeartbeatAt ? 'son sinyal ' + ago(r.lastHeartbeatAt) : 'hiç bağlanmadı'}</span>
      )}
      <div className="k-runner-foot">
        <button className="k-link mono" disabled={issueToken.isPending} onClick={getToken}>
          {token ? 'YENİ TOKEN' : 'BRIDGE TOKEN OLUŞTUR'}
        </button>
        <button
          className="k-link mono"
          onClick={async () => {
            await remove.mutateAsync(r.id);
            toast('Runner kaldırıldı', r.label, 'var(--st-cancel)', 'x');
          }}
        >
          KALDIR
        </button>
      </div>
      {r.status === 'ONLINE' && <TerminalAccessFlow runnerId={r.id} />}
    </Reveal>
  );
}

function PendingTerminalRequestsPanel() {
  const pendingQuery = usePendingTerminalSessionsQuery();
  const decide = useDecideTerminalSessionMutation();
  const toast = useToast();
  const pending = pendingQuery.data ?? [];

  if (pending.length === 0) return null;

  const respond = async (request: RunnerTerminalSessionResponse, decision: 'approve' | 'reject') => {
    await decide.mutateAsync({ requestId: request.id, decision });
    toast(
      decision === 'approve' ? 'Terminal erişimi onaylandı' : 'Terminal erişimi reddedildi',
      request.runnerConnectionId.slice(0, 8),
      decision === 'approve' ? 'var(--st-ok)' : 'var(--st-fail)',
      decision === 'approve' ? 'check' : 'x',
    );
  };

  return (
    <Panel kicker="İNSAN KAPISI" title="Bekleyen terminal istekleri" className="k-rv">
      <div className="k-form-grid">
        {pending.map((req) => (
          <div key={req.id} className="k-meta-row">
            <span className="k-meta-l mono">runner {req.runnerConnectionId.slice(0, 8)} · talep {req.requestedBy.slice(0, 8)}</span>
            <span className="k-meta-v" style={{ display: 'flex', gap: 'var(--space-2)' }}>
              <Btn kind="pri" icon="check" disabled={decide.isPending} onClick={() => respond(req, 'approve')}>Onayla</Btn>
              <Btn kind="dan" icon="x" disabled={decide.isPending} onClick={() => respond(req, 'reject')}>Reddet</Btn>
            </span>
          </div>
        ))}
      </div>
    </Panel>
  );
}
