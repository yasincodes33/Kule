import { useState } from 'react';
import { ApiError } from '../../shared/api/client';
import { useAuthStore } from '../../shared/auth/authStore';
import { Btn, Field, Panel } from '../../shared/components/primitives';
import { useToast } from '../../shared/components/Toasts';
import { useChangePasswordMutation, useUpdateProfileMutation } from './hooks';

export default function ProfilePage() {
  return (
    <div className="k-page">
      <div className="k-page-h">
        <div>
          <span className="k-kicker mono">HESAP</span>
          <h3>Profil</h3>
        </div>
      </div>
      <div className="k-org-grid">
        <ProfileForm />
        <PasswordForm />
      </div>
    </div>
  );
}

function ProfileForm() {
  const user = useAuthStore((s) => s.user);
  const [displayName, setDisplayName] = useState(user?.displayName ?? '');
  const update = useUpdateProfileMutation();
  const toast = useToast();
  const error = update.error instanceof ApiError ? update.error.message : update.error ? 'Güncellenemedi' : null;

  const submit = async () => {
    await update.mutateAsync(displayName.trim());
    toast('Profil güncellendi', displayName.trim() || 'ad-soyad temizlendi', 'var(--st-ok)', 'check');
  };

  return (
    <Panel kicker="HESAP BİLGİLERİ" title="Profil">
      {error && <p className="k-sub k-err" style={{ marginBottom: 'var(--space-3)' }}>{error}</p>}
      <Field label="E-POSTA">
        <input className="k-input mono" value={user?.email ?? ''} disabled />
      </Field>
      <Field label="AD SOYAD">
        <input className="k-input mono" value={displayName} onChange={(e) => setDisplayName(e.target.value)} placeholder="Deniz Aksoy" />
      </Field>
      <Btn kind="pri" icon="check" disabled={update.isPending} onClick={submit}>
        {update.isPending ? 'KAYDEDİLİYOR…' : 'Kaydet'}
      </Btn>
    </Panel>
  );
}

function PasswordForm() {
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [next2, setNext2] = useState('');
  const changePassword = useChangePasswordMutation();
  const toast = useToast();
  const error = changePassword.error instanceof ApiError ? changePassword.error.message : changePassword.error ? 'Şifre değiştirilemedi' : null;
  const matchOk = next === next2;
  const valid = current.length > 0 && next.length >= 8 && matchOk;

  const submit = async () => {
    if (!valid) return;
    await changePassword.mutateAsync({ currentPassword: current, newPassword: next });
    setCurrent('');
    setNext('');
    setNext2('');
    toast('Şifre değiştirildi', undefined, 'var(--st-ok)', 'check');
  };

  return (
    <Panel kicker="GÜVENLİK" title="Şifre değiştir">
      {error && <p className="k-sub k-err" style={{ marginBottom: 'var(--space-3)' }}>{error}</p>}
      <Field label="MEVCUT ŞİFRE">
        <input className="k-input mono" type="password" value={current} onChange={(e) => setCurrent(e.target.value)} placeholder="••••••••" />
      </Field>
      <Field label="YENİ ŞİFRE" hint="en az 8 karakter">
        <input className="k-input mono" type="password" value={next} onChange={(e) => setNext(e.target.value)} placeholder="••••••••" />
      </Field>
      <Field label="YENİ ŞİFRE (TEKRAR)">
        <input className="k-input mono" type="password" value={next2} onChange={(e) => setNext2(e.target.value)} placeholder="••••••••" />
      </Field>
      {next2.length > 0 && !matchOk && <p className="k-sub k-err" style={{ marginTop: '-8px', marginBottom: 'var(--space-3)' }}>şifreler eşleşmiyor</p>}
      <Btn kind="pri" icon="check" disabled={!valid || changePassword.isPending} onClick={submit}>
        {changePassword.isPending ? 'DEĞİŞTİRİLİYOR…' : 'Şifreyi değiştir'}
      </Btn>
    </Panel>
  );
}
