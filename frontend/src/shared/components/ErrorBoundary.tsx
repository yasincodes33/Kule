import { Component, type ErrorInfo, type ReactNode } from 'react';

/**
 * Bir sayfa bileşeni render sırasında hata fırlatırsa React tüm ağacı söküyor ve geriye
 * BOMBOŞ (koyu temada siyah) bir ekran kalıyor — kullanıcı için hiçbir ipucu yok.
 *
 * Canlı örnek: onay kuyruğunda `requestedBy` NULL geldiğinde `null.slice()` patlıyordu;
 * bildirim düşüyor ama ekran açılmıyordu. O hata düzeltildi, ama sınıf olarak aynı şeyin
 * tekrar siyah ekrana dönüşmemesi için sınır burada.
 */
interface State {
  error: Error | null;
}

export class ErrorBoundary extends Component<{ children: ReactNode }, State> {
  state: State = { error: null };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('Sayfa render edilirken hata:', error, info.componentStack);
  }

  render() {
    if (!this.state.error) {
      return this.props.children;
    }
    return (
      <div className="k-page">
        <div className="k-page-h">
          <div>
            <span className="k-kicker mono">BEKLENMEYEN HATA</span>
            <h3>Bu sayfa açılamadı</h3>
          </div>
        </div>
        <p className="k-sub">
          Sayfa render edilirken bir hata oluştu. Diğer sayfalar çalışmaya devam ediyor —
          soldaki menüden başka bir sayfaya geçebilirsin.
        </p>
        <pre className="mono k-sub" style={{ whiteSpace: 'pre-wrap', marginTop: 'var(--space-3)' }}>
          {this.state.error.message}
        </pre>
        <button className="k-link mono" style={{ marginTop: 'var(--space-3)' }} onClick={() => this.setState({ error: null })}>
          tekrar dene →
        </button>
      </div>
    );
  }
}
