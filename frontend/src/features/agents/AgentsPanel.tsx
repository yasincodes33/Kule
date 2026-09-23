import { useState } from 'react';
import { ApiError } from '../../shared/api/client';
import { AgentTypeMark, Btn, CapTag, Field, Panel, Reveal } from '../../shared/components/primitives';
import { useToast } from '../../shared/components/Toasts';
import { AGENT_TYPE, TASK_TYPES, type AgentType, type TaskType } from '../../shared/domain/enums';
import { useAddAgentCapabilityMutation, useAgentConnectionsQuery, useRegisterAgentMutation, useRemoveAgentMutation } from './hooks';
import type { AgentConnectionResponse } from '../../shared/api/types';

const AGENT_TYPES: AgentType[] = ['CLAUDE', 'CHATGPT', 'GEMINI'];

export function AgentsPanel() {
  const agentsQuery = useAgentConnectionsQuery();
  const agents = agentsQuery.data ?? [];
  const [open, setOpen] = useState(false);
  const online = agents.filter((a) => a.status === 'ONLINE').length;

  return (
    <>
      <div className="k-page-actions" style={{ justifyContent: 'space-between' }}>
        <span className="mono k-sub">{online}/{agents.length} online</span>
        <Btn kind="pri" icon="plus" onClick={() => setOpen(!open)}>Ajan bağla</Btn>
      </div>

      {open && <RegisterAgentForm onDone={() => setOpen(false)} />}

      {agentsQuery.isLoading ? (
        <p className="k-sub">Yükleniyor…</p>
      ) : agents.length === 0 ? (
        <Panel kicker="BOŞ" title="Henüz bağlı ajan yok">
          <p className="k-sub">Claude, ChatGPT veya Gemini API anahtarını bağlamak için yukarıdaki "Ajan bağla" düğmesini kullan.</p>
        </Panel>
      ) : (
        <div className="k-runner-grid">
          {agents.map((a, i) => <AgentCard key={a.id} a={a} i={i} />)}
        </div>
      )}
    </>
  );
}

function RegisterAgentForm({ onDone }: { onDone: () => void }) {
  const [agentType, setAgentType] = useState<AgentType>('CLAUDE');
  const [apiKey, setApiKey] = useState('');
  const [caps, setCaps] = useState<TaskType[]>(['DEV']);
  const register = useRegisterAgentMutation();
  const toast = useToast();
  const error = register.error instanceof ApiError ? register.error.message : register.error ? 'Ajan bağlanamadı' : null;
  const toggleCap = (c: TaskType) => setCaps((cs) => (cs.includes(c) ? cs.filter((x) => x !== c) : [...cs, c]));
  const valid = apiKey.trim().length > 0 && caps.length > 0;

  const submit = async () => {
    await register.mutateAsync({ agentType, capabilities: caps, apiKey: apiKey.trim() });
    toast('Ajan bağlandı', AGENT_TYPE[agentType].name, 'var(--st-ok)', 'check');
    onDone();
  };

  return (
    <Panel kicker="YENİ BAĞLANTI" title="Ajan bağla" className="k-rv">
      {error && <p className="k-sub k-err" style={{ marginBottom: 'var(--space-3)' }}>{error}</p>}
      <div className="k-form-grid">
        <Field label="SAĞLAYICI">
          <div className="k-seg2 full">
            {AGENT_TYPES.map((a) => (
              <button key={a} className={agentType === a ? 'on' : ''} onClick={() => setAgentType(a)}><span className="mono">{AGENT_TYPE[a].name}</span></button>
            ))}
          </div>
        </Field>
        <Field label="API ANAHTARI" hint="yalnızca kaydedilir, bir daha gösterilmez">
          <input className="k-input mono" type="password" value={apiKey} onChange={(e) => setApiKey(e.target.value)} placeholder="sk-••••••••" />
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

function AgentCard({ a, i }: { a: AgentConnectionResponse; i: number }) {
  const remove = useRemoveAgentMutation();
  const addCap = useAddAgentCapabilityMutation();
  const toast = useToast();

  return (
    <Reveal i={i} className={'k-runner k-runner-' + a.status.toLowerCase()}>
      <div className="k-runner-h">
        <div className="k-runner-id">
          <span className={'k-pulse' + (a.status === 'ONLINE' ? '' : ' k-pulse-offline')} />
          <AgentTypeMark a={a.agentType} s={20} showName />
        </div>
        <span className={'mono k-runner-state' + (a.status === 'ONLINE' ? ' k-rs-online' : '')}>{a.status}</span>
      </div>
      <div className="mono k-runner-host">{a.id.slice(0, 8)}</div>
      <div className="k-runner-caps">
        {TASK_TYPES.map((c) =>
          a.capabilities.includes(c) ? (
            <CapTag key={c} cap={c} on />
          ) : (
            <button
              key={c}
              className="k-cap mono k-cap-off"
              style={{ cursor: 'pointer' }}
              disabled={addCap.isPending}
              title={`${c} yeteneği ekle`}
              onClick={() => addCap.mutate({ connectionId: a.id, taskType: c })}
            >
              +{c}
            </button>
          ),
        )}
      </div>
      <div className="k-runner-foot">
        <span className="mono k-sub" />
        <button
          className="k-link mono"
          onClick={async () => {
            await remove.mutateAsync(a.id);
            toast('Ajan kaldırıldı', AGENT_TYPE[a.agentType].name, 'var(--st-cancel)', 'x');
          }}
        >
          KALDIR
        </button>
      </div>
    </Reveal>
  );
}
