import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ApiError } from '../../shared/api/client';
import { useAuthStore } from '../../shared/auth/authStore';
import { AuthLayout } from '../../shared/components/AuthLayout';
import { Btn, Field } from '../../shared/components/primitives';
import { Icon } from '../../shared/components/Icon';
import { authApi } from './api';

const TICKER: [string, string][] = [
  ['OK', 'runner atlas-01 · heartbeat 42ms'],
  ['GATE', 'KT-2480 onay bekliyor · prod ingress'],
  ['RUN', 'KT-2481 · adım 14/22'],
  ['OK', 'KT-2474 tamamlandı · exit 0'],
  ['WARN', 'deploy-1 yük %94 · kuyruk devrediliyor'],
  ['RUN', 'KT-2479 · 2.4M satır tarandı'],
  ['OK', 'ingest-1 · 3 görev devralındı'],
  ['GATE', 'KT-2478 · CRITICAL · iam politikası'],
];

export default function LoginPage() {
  const [mode, setMode] = useState<'in' | 'up'>('in');
  const [email, setEmail] = useState('');
  const [pw, setPw] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [line, setLine] = useState(0);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const setSession = useAuthStore((s) => s.setSession);
  const navigate = useNavigate();

  useEffect(() => {
    const t = setInterval(() => setLine((n) => n + 1), 1800);
    return () => clearInterval(t);
  }, []);

  const emailOk = /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email);
  const pwOk = mode === 'in' ? pw.length > 0 : pw.length >= 8;
  const ok = emailOk && pwOk && !busy;
  const visible = Array.from({ length: 5 }, (_, i) => TICKER[(line + i) % TICKER.length]);

  const submit = async () => {
    if (!ok) return;
    setBusy(true);
    setError(null);
    try {
      const auth = mode === 'in' ? await authApi.login(email, pw) : await authApi.register(email, pw, displayName);
      setSession(auth);
      navigate('/', { replace: true });
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Bağlantı kurulamadı — backend çalışıyor mu?');
    } finally {
      setBusy(false);
    }
  };

  return (
    <AuthLayout
      heading={<>AI ajanları için<br />kontrol kulesi.</>}
      tagline="Görevi dağıt, akışı canlı izle, riskli her araç çağrısını insan onayından geçir. Tek kuyrukta çok organizasyonlu görev akışı."
      brandExtra={
        <div className="k-auth-ticker mono">
          {visible.map((l, i) => (
            <div key={line + '-' + i} className="k-tick" style={{ '--o': 1 - i * 0.2 } as React.CSSProperties}>
              <span className={'k-tick-l k-tick-' + l[0].toLowerCase()}>{l[0]}</span>
              <span>{l[1]}</span>
            </div>
          ))}
        </div>
      }
    >
      <div className="k-seg2 full">
        <button className={mode === 'in' ? 'on' : ''} onClick={() => { setMode('in'); setError(null); }}>
          <span className="mono">GİRİŞ</span>
        </button>
        <button className={mode === 'up' ? 'on' : ''} onClick={() => { setMode('up'); setError(null); }}>
          <span className="mono">KAYIT</span>
        </button>
      </div>
      <h4 className="k-auth-t">{mode === 'in' ? 'Kuleye dön' : 'Kuleye katıl'}</h4>
      <p className="k-sub k-auth-sub">
        {mode === 'in' ? 'E-posta ile organizasyonuna bağlan.' : 'Hesabını oluştur, sonra organizasyonunu kur veya bir davet kabul et.'}
      </p>
      {error && (
        <div className="k-auth-err mono">
          <Icon n="alert" s={14} />
          <span>{error}</span>
        </div>
      )}
      {mode === 'up' && (
        <Field label="AD SOYAD" hint="opsiyonel">
          <input className="k-input mono" value={displayName} onChange={(e) => setDisplayName(e.target.value)} placeholder="Deniz Aksoy" />
        </Field>
      )}
      <Field label="E-POSTA">
        <input className="k-input mono" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="deniz@sirket.com" />
      </Field>
      <Field label="ŞİFRE" hint={mode === 'up' ? 'en az 8 karakter' : undefined}>
        <input
          className="k-input mono"
          type="password"
          value={pw}
          onChange={(e) => setPw(e.target.value)}
          placeholder="••••••••"
          onKeyDown={(e) => e.key === 'Enter' && submit()}
        />
      </Field>
      {mode === 'in' && (
        <Link to="/forgot-password" className="k-link mono" style={{ display: 'block', marginBottom: 'var(--space-3)' }}>
          şifremi unuttum
        </Link>
      )}
      <Btn kind="pri" icon="arrow" className="k-btn-block" disabled={!ok} onClick={submit}>
        {busy ? 'BAĞLANIYOR…' : mode === 'in' ? 'Kuleye gir' : 'Hesap oluştur'}
      </Btn>
      <div className="k-auth-foot mono">
        <span>SOC 2 TYPE II</span>
        <span>·</span>
        <span>DENETİM KAYDI</span>
        <span>·</span>
        <span>AB VERİ İKAMETİ</span>
      </div>
    </AuthLayout>
  );
}
