import { useEffect, useRef, useState } from 'react';
import { useAuthStore } from '../../shared/auth/authStore';
import { taskApi } from './api';

const WS_BASE = import.meta.env.VITE_WS_BASE_URL ?? 'ws://localhost:8081';

export interface LiveLogEntry {
  id: string;
  level: string;
  message: string;
  timestamp: string;
  status: string | null;
}

/**
 * `/ws/tasks/{taskId}/logs` — yalnızca sunucudan istemciye tek yönlü push (bkz.
 * TaskLogStreamHandler/TaskLogPublisherImpl). Tarayıcının native WebSocket API'si handshake'e
 * özel header koyamadığı için kimlik query string ile taşınıyor — ama [Genel denetim #4 —
 * güvenlik] artık asıl (uzun ömürlü) access token'ı DEĞİL, her bağlantı denemesinden önce normal
 * bir HTTP isteğiyle (Authorization header'ıyla, `taskApi.issueLogsWsTicket`) alınan tek
 * kullanımlık, 60 saniye geçerli bir bilet taşınıyor (?ticket=...) — bkz.
 * TaskLogStreamAuthInterceptor/WsTicketService. Sızsa bile ya süresi geçmiş ya da tüketilmiş olur.
 */
export function useTaskLogStream(taskId: string | null) {
  const accessToken = useAuthStore((s) => s.accessToken);
  const [live, setLive] = useState<LiveLogEntry[]>([]);
  const [connected, setConnected] = useState(false);
  const socketRef = useRef<WebSocket | null>(null);

  useEffect(() => {
    if (!taskId || !accessToken) return;
    let cancelled = false;
    let retryTimer: ReturnType<typeof setTimeout>;

    const connect = async () => {
      if (cancelled) return;
      let ticket: string;
      try {
        ({ ticket } = await taskApi.issueLogsWsTicket(taskId));
      } catch {
        if (!cancelled) retryTimer = setTimeout(connect, 3000);
        return;
      }
      if (cancelled) return;

      const url = `${WS_BASE}/ws/tasks/${taskId}/logs?ticket=${encodeURIComponent(ticket)}`;
      const socket = new WebSocket(url);
      socketRef.current = socket;

      socket.onopen = () => setConnected(true);
      socket.onclose = () => {
        setConnected(false);
        if (!cancelled) retryTimer = setTimeout(connect, 3000);
      };
      socket.onerror = () => socket.close();
      socket.onmessage = (event) => {
        try {
          const msg = JSON.parse(event.data) as LiveLogEntry;
          setLive((prev) => [...prev, msg].slice(-500));
        } catch {
          // JSON olmayan bir çerçeve geldiyse yok say
        }
      };
    };

    connect();
    return () => {
      cancelled = true;
      clearTimeout(retryTimer);
      socketRef.current?.close();
    };
  }, [taskId, accessToken]);

  return { live, connected };
}
