import { FitAddon } from '@xterm/addon-fit';
import { Terminal } from '@xterm/xterm';
import '@xterm/xterm/css/xterm.css';
import { useEffect, useRef, useState } from 'react';
import { runnerTerminalApi } from './terminalApi';

const WS_BASE = import.meta.env.VITE_WS_BASE_URL ?? 'ws://localhost:8081';

type ConnState = 'connecting' | 'connected' | 'closed' | 'error';

/**
 * Onaylanmış bir RunnerTerminalSessionRequest üzerinden runner'a canlı shell bağlantısı açar.
 * useTaskLogStream.ts'teki "önce bilet al, sonra WS aç" deseninin aynısı — asıl access token
 * hiçbir zaman WS URL'sine taşınmıyor (bkz. issueTerminalWsTicket/RunnerTerminalAuthInterceptor).
 * Runner tarafı gerçek bir PTY değil (child_process.spawn) — tam TTY sadakati yok ama günlük
 * git/build/dosya komutları için yeterli, bkz. plan dokümanındaki PTY kararı.
 */
export function TerminalPanel({ runnerId, requestId, initialCommand, taskId, onClose }: {
  runnerId: string;
  requestId: string;
  /** Bağlantı kurulur kurulmaz otomatik "yazılıp" Enter'a basılacak komut (opsiyonel) — bkz.
   * TerminalAccessFlow'daki AI hızlı başlatma düğmesi. Kullanıcının kendi yazdığından backend/
   * runner açısından hiçbir farkı yok, yalnızca frontend'in gönderdiği ilk 'input' mesajı. */
  initialCommand?: string;
  /** Verilirse, oturum kapandığında çıktının tamamı bu görevin kalıcı log akışına yazılır
   * (bkz. backend Faz B — AgentBridgeHandler'ın TERMINAL_CLOSED'da yaptığı flush). */
  taskId?: string;
  onClose: () => void;
}) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const socketRef = useRef<WebSocket | null>(null);
  const [status, setStatus] = useState<ConnState>('connecting');

  useEffect(() => {
    let cancelled = false;
    const term = new Terminal({
      convertEol: true,
      fontSize: 13,
      fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Consolas, monospace',
      theme: { background: '#0b0e14', foreground: '#d6dee8' },
    });
    const fit = new FitAddon();
    term.loadAddon(fit);
    if (containerRef.current) {
      term.open(containerRef.current);
      fit.fit();
    }

    const sendJson = (payload: unknown) => {
      if (socketRef.current?.readyState === WebSocket.OPEN) {
        socketRef.current.send(JSON.stringify(payload));
      }
    };

    const connect = async () => {
      let ticket: string;
      try {
        ({ ticket } = await runnerTerminalApi.issueWsTicket(runnerId, requestId, taskId));
      } catch {
        if (!cancelled) setStatus('error');
        return;
      }
      if (cancelled) return;

      const url = `${WS_BASE}/ws/runners/${runnerId}/terminal?ticket=${encodeURIComponent(ticket)}`;
      const socket = new WebSocket(url);
      socketRef.current = socket;

      socket.onopen = () => {
        if (cancelled) return;
        setStatus('connected');
        sendJson({ type: 'resize', cols: term.cols, rows: term.rows });
        if (initialCommand) sendJson({ type: 'input', data: initialCommand + '\r' });
      };
      socket.onclose = () => {
        if (!cancelled) setStatus('closed');
      };
      socket.onerror = () => socket.close();
      socket.onmessage = (event) => {
        try {
          const msg = JSON.parse(event.data) as { type: string; data?: string };
          if (msg.type === 'output' && msg.data) {
            term.write(msg.data);
          } else if (msg.type === 'closed') {
            term.write('\r\n\x1b[33m[oturum runner tarafından kapatıldı]\x1b[0m\r\n');
            setStatus('closed');
          }
        } catch {
          // JSON olmayan bir çerçeve geldiyse yok say
        }
      };
    };

    connect();

    const dataDisposable = term.onData((data) => sendJson({ type: 'input', data }));
    const resizeDisposable = term.onResize(({ cols, rows }) => sendJson({ type: 'resize', cols, rows }));

    const onWindowResize = () => fit.fit();
    window.addEventListener('resize', onWindowResize);

    return () => {
      cancelled = true;
      window.removeEventListener('resize', onWindowResize);
      dataDisposable.dispose();
      resizeDisposable.dispose();
      socketRef.current?.close();
      term.dispose();
    };
  }, [runnerId, requestId]);

  const statusLabel =
    status === 'connecting' ? 'bağlanıyor…' : status === 'connected' ? 'bağlı' : status === 'closed' ? 'oturum kapandı' : 'bağlantı hatası';

  return (
    <div style={{ position: 'fixed', inset: 0, display: 'grid', placeItems: 'center', background: 'rgba(0,0,0,.7)', zIndex: 60 }} onClick={onClose}>
      <div
        style={{
          width: 'min(920px, 94vw)',
          height: 'min(560px, 80vh)',
          display: 'flex',
          flexDirection: 'column',
          background: 'var(--panel)',
          border: '1px solid var(--line)',
          borderRadius: 8,
          overflow: 'hidden',
        }}
        onClick={(e) => e.stopPropagation()}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '8px 12px', borderBottom: '1px solid var(--line)' }}>
          <span className="mono k-sub">
            <span className={'k-pulse' + (status === 'connected' ? '' : ' k-pulse-offline')} style={{ marginRight: 8 }} />
            TERMİNAL · {statusLabel}
          </span>
          <button className="k-link mono" onClick={onClose}>KAPAT</button>
        </div>
        <div ref={containerRef} style={{ flex: 1, padding: 8, minHeight: 0 }} />
      </div>
    </div>
  );
}
