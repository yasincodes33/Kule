import { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { ApiError } from '../../shared/api/client';
import { AuthLayout } from '../../shared/components/AuthLayout';
import { Btn, Field } from '../../shared/components/primitives';
import { Icon } from '../../shared/components/Icon';
import { authApi } from './api';

export default function ResetPasswordPage() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token') ?? '';
  const navigate = useNavigate();
  const [pw, setPw] = useState('');
  const [pw2, setPw2] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const pwOk = pw.length >= 8;
  const matchOk = pw === pw2;
  const ok = !!token && pwOk && matchOk && !busy;

  const submit = async () => {
    if (!ok) return;
    setBusy(true);
    setError(null);
    try {
      await authApi.resetPassword(token, pw);
      navigate('/login', { replace: true });
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Bağlantı kurulamadı — backend çalışıyor mu?');
    } finally {
      setBusy(false);
    }
  };

  return (
    <AuthLayout heading={<>Yeni bir<br />şifre belirle.</>} tagline="Sıfırlama bağlantısındaki token ile yeni şifreni ayarla.">
      <h4 className="k-auth-t">Şifreyi sıfırla</h4>
      <p className="k-sub k-auth-sub">Yeni şifreni iki kez gir.</p>

      {!token && (
        <div className="k-auth-err mono">
          <Icon n="alert" s={14} />
          <span>Bağlantıda token eksik — sıfırlama e-postasındaki/log'undaki bağlantıyı olduğu gibi kullan.</span>
        </div>
      )}
      {error && (
        <div className="k-auth-err mono">
          <Icon n="alert" s={14} />
          <span>{error}</span>
        </div>
      )}

      <Field label="YENİ ŞİFRE" hint="en az 8 karakter">
        <input className="k-input mono" type="password" value={pw} onChange={(e) => setPw(e.target.value)} placeholder="••••••••" autoFocus />
      </Field>
      <Field label="YENİ ŞİFRE (TEKRAR)">
        <input
          className="k-input mono"
          type="password"
          value={pw2}
          onChange={(e) => setPw2(e.target.value)}
          placeholder="••••••••"
          onKeyDown={(e) => e.key === 'Enter' && submit()}
        />
      </Field>
      {pw2.length > 0 && !matchOk && <p className="k-sub k-err" style={{ marginTop: '-8px', marginBottom: 'var(--space-3)' }}>şifreler eşleşmiyor</p>}

      <Btn kind="pri" icon="check" className="k-btn-block" disabled={!ok} onClick={submit}>
        {busy ? 'GÜNCELLENİYOR…' : 'Şifreyi güncelle'}
      </Btn>

      <Link to="/login" className="k-link mono" style={{ display: 'block', marginTop: 'var(--space-4)' }}>
        ← girişe dön
      </Link>
    </AuthLayout>
  );
}
