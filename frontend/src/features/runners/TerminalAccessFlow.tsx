import { useState } from 'react';
import { useToast } from '../../shared/components/Toasts';
import { TerminalPanel } from './TerminalPanel';
import { useRequestTerminalSessionMutation, useTerminalSessionQuery } from './terminalHooks';

/**
 * Canlı terminal erişimi — kullanıcı iki temel karara zaten onay verdi: erişim türü "canlı
 * terminal", güvenlik modeli "oturumun AÇILMASI onay kapısından geçsin" (tuş başına değil).
 * Akış: iste → PENDING (onaylayıcı bekleniyor, useTerminalSessionQuery kısa aralıkla yoklar) →
 * APPROVED (Bağlan düğmesi belirir) → TerminalPanel (xterm.js, gerçek WS bağlantısı).
 *
 * Hem RunnersPage hem TaskDetailPage kullanabilsin diye ayrı bir bileşen; TaskDetailPage'in
 * "bu araçla canlı terminalde başlat" akışının da AYNI state machine'e ihtiyacı olduğu için
 * buraya taşınıp `initialCommand` ile parametrik hale getirildi (tek kaynak, iki kullanım yeri).
 */
export function TerminalAccessFlow({
  runnerId,
  initialCommand,
  taskId,
  launchLabel = 'CANLI TERMİNAL İSTE',
  disabled,
  note,
  onBeforeStart,
}: {
  runnerId: string;
  /** Bağlantı kurulunca otomatik çalıştırılacak komut (opsiyonel) — bkz. TerminalPanel. */
  initialCommand?: string;
  /** Verilirse çıktı bu görevin kalıcı log akışına yazılır — bkz. TerminalPanel/backend Faz B. */
  taskId?: string;
  launchLabel?: string;
  /** true ise istek düğmesi tıklanamaz (ör. runner offline, görev henüz dağıtılmadı). */
  disabled?: boolean;
  /** Düğmenin altında her zaman gösterilen bilgi notu — disabled olsun olmasın (ör. "bu araç
   * için otomatik komut tanımlı değil, elle çalıştır" gibi devre dışı OLMAYAN bir uyarı için). */
  note?: string;
  /** İstek gönderilmeden hemen önce çalıştırılır (ör. bir şeyi kaydetmek için) — tek tıkla
   * "kaydet + başlat" akışları için. */
  onBeforeStart?: () => Promise<void>;
}) {
  const [requestId, setRequestId] = useState<string | null>(null);
  const [panelOpen, setPanelOpen] = useState(false);
  const requestSession = useRequestTerminalSessionMutation();
  const sessionQuery = useTerminalSessionQuery(requestId);
  const toast = useToast();
  const session = sessionQuery.data;

  const start = async () => {
    if (onBeforeStart) await onBeforeStart();
    const created = await requestSession.mutateAsync(runnerId);
    setRequestId(created.id);
    toast('Terminal isteği gönderildi', 'Bir onaylayıcının onayı bekleniyor', 'var(--color-accent)', 'terminal');
  };

  if (panelOpen && requestId) {
    return <TerminalPanel runnerId={runnerId} requestId={requestId} initialCommand={initialCommand} taskId={taskId} onClose={() => setPanelOpen(false)} />;
  }

  if (!requestId || session?.status === 'REJECTED' || session?.status === 'EXPIRED') {
    return (
      <div>
        <button className="k-link mono" disabled={disabled || requestSession.isPending} onClick={start} style={{ marginTop: 'var(--space-2)' }}>
          {session?.status === 'REJECTED' ? 'REDDEDİLDİ · TEKRAR İSTE' : session?.status === 'EXPIRED' ? 'SÜRESİ DOLDU · TEKRAR İSTE' : launchLabel}
        </button>
        {note && <div className="mono k-sub" style={{ marginTop: 'var(--space-1)' }}>{note}</div>}
      </div>
    );
  }

  if (session?.status === 'PENDING') {
    return <span className="mono k-sub" style={{ display: 'block', marginTop: 'var(--space-2)' }}>terminal isteği onay bekliyor…</span>;
  }

  if (session?.status === 'APPROVED') {
    return (
      <button className="k-link mono" onClick={() => setPanelOpen(true)} style={{ marginTop: 'var(--space-2)' }}>
        TERMİNALE BAĞLAN →
      </button>
    );
  }

  return null;
}
