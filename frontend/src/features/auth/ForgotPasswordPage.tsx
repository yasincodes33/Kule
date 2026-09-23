import { useState } from 'react';
import { Link } from 'react-router-dom';
import { ApiError } from '../../shared/api/client';
import { AuthLayout } from '../../shared/components/AuthLayout';
import { Btn, Field } from '../../shared/components/primitives';
import { authApi } from './api';

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [sent, setSent] = useState(false);

  const emailOk = /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email);

  const submit = async () => {
    if (!emailOk || busy) return;
    setBusy(true);
    setError(null);
    try {
      await authApi.forgotPassword(email);
      setSent(true);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Bağlantı kurulamadı — backend çalışıyor mu?');
    } finally {
      setBusy(false);
    }
  };

  return (
    <AuthLayout
      heading={<>Şifreni<br />sıfırla.</>}
      tagline="E-postanı gir, hesabın varsa bir sıfırlama bağlantısı hazırlanır."
    >
      <h4 className="k-auth-t">Şifremi unuttum</h4>
      <p className="k-sub k-auth-sub">Kayıtlı e-posta adresini gir.</p>

      {sent ? (
        <div className="k-auth-err mono" style={{ color: 'var(--st-ok)', borderColor: 'color-mix(in srgb, var(--st-ok) 45%, transparent)', background: 'color-mix(in srgb, var(--st-ok) 12%, transparent)' }}>
          <span>Bu e-posta kayıtlıysa, sıfırlama bağlantısı hazırlandı. Bu ortamda henüz gerçek e-posta gönderimi yok — bağlantı backend konsol log'unda görünüyor.</span>
        </div>
      ) : (
        <>
          {error && <div className="k-auth-err mono"><span>{error}</span></div>}
          <Field label="E-POSTA">
            <input
              className="k-input mono"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="deniz@sirket.com"
              onKeyDown={(e) => e.key === 'Enter' && submit()}
              autoFocus
            />
          </Field>
          <Btn kind="pri" icon="arrow" className="k-btn-block" disabled={!emailOk || busy} onClick={submit}>
            {busy ? 'GÖNDERİLİYOR…' : 'Sıfırlama bağlantısı gönder'}
          </Btn>
        </>
      )}

      <Link to="/login" className="k-link mono" style={{ display: 'block', marginTop: 'var(--space-4)' }}>
        ← girişe dön
      </Link>
    </AuthLayout>
  );
}
