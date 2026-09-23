import { useEffect, useRef, useState } from 'react';
import { logLevelColor } from '../../shared/domain/enums';
import { stamp } from '../../shared/utils/format';

export interface LogLine {
  id: string;
  level: string;
  message: string;
  timestamp: string;
}

export function LogConsole({ taskId, logs, connected }: { taskId: string; logs: LogLine[]; connected: boolean }) {
  const box = useRef<HTMLDivElement>(null);
  const [stick, setStick] = useState(true);
  const [only, setOnly] = useState<'all' | 'ai' | 'terminal' | 'tool' | 'warn'>('all');

  const rows = logs.filter((l) => {
    if (only === 'all') return true;
    const lvl = l.level.toUpperCase();
    // Görevi çalıştıran kişinin masaüstü terminalinde ne yaptığı — çoğunlukla akışın en hacimli
    // kısmı olduğu için ayrı bir filtresi var (hem izole etmek hem de elemek için).
    if (only === 'terminal') return lvl === 'TERMINAL' || lvl === 'MODEL';
    // AI yardımcılarının kayıtları — plan / inceleme / özet.
    if (only === 'ai') return lvl === 'TRIAGE' || lvl === 'REVIEW' || lvl === 'SUMMARY';
    if (only === 'tool') return lvl === 'TOOL';
    return lvl === 'WARN' || lvl === 'WARNING' || lvl === 'ERROR' || lvl === 'ERR' || lvl === 'GATE';
  });

  useEffect(() => {
    const el = box.current;
    if (el && stick) el.scrollTop = el.scrollHeight;
  }, [logs.length, stick, only]);

  const onScroll = () => {
    const el = box.current;
    if (!el) return;
    setStick(el.scrollHeight - el.scrollTop - el.clientHeight < 40);
  };

  return (
    <div className="k-console">
      <div className="k-console-h">
        <div className="k-console-h-l">
          <span className="mono k-console-title">agent.stream · {taskId.slice(0, 8)}</span>
          <span className={'mono k-ws' + (connected ? ' on' : '')}>{connected ? 'WS BAĞLI' : 'BAĞLANIYOR…'}</span>
        </div>
        <div className="k-console-h-r">
          {(
            [
              ['all', 'TÜMÜ'],
              ['ai', 'AI'],
              ['terminal', 'TERMİNAL'],
              ['tool', 'ARAÇ'],
              ['warn', 'UYARI'],
            ] as const
          ).map(([k, l]) => (
            <button key={k} className={'k-chip mono sm' + (only === k ? ' on' : '')} onClick={() => setOnly(k)}>
              {l}
            </button>
          ))}
        </div>
      </div>
      <div className="k-console-b mono" ref={box} onScroll={onScroll}>
        {rows.map((l) => (
          <div key={l.id} className="k-logline k-logline-in" style={{ '--c': logLevelColor(l.level) } as React.CSSProperties}>
            <span className="k-log-t">{stamp(l.timestamp)}</span>
            <span className="k-log-l">{l.level.toUpperCase()}</span>
            <span className="k-log-x">{l.message}</span>
          </div>
        ))}
        {rows.length === 0 && <div className="k-logline-empty"><span className="k-log-x k-sub">henüz log yok — görev kuyrukta bekliyor</span></div>}
        {connected && <div className="k-caret" />}
      </div>
      {!stick && (
        <button className="k-jump mono" onClick={() => setStick(true)}>
          ↓ canlı akışa dön
        </button>
      )}
    </div>
  );
}
