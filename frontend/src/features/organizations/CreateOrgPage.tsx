import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ApiError } from '../../shared/api/client';
import { useAuthStore } from '../../shared/auth/authStore';
import { AuthLayout } from '../../shared/components/AuthLayout';
import { Btn, Field, Panel } from '../../shared/components/primitives';
import { InvitationsPanel } from './InvitationsPanel';
import { useCreateOrganizationMutation } from './hooks';

/**
 * Backend'in RegisterRequest'i org bilgisi almıyor (kayıt yalnızca kullanıcı oluşturur) —
 * bu yüzden Claude Design mockup'ındaki tek-adımlı "kayıt = org kurulumu" akışı yerine,
 * ilk girişte hiç organizasyonu olmayan kullanıcı için ayrı bir onboarding adımı gerekiyor.
 */
export default function CreateOrgPage() {
  const [name, setName] = useState('');
  const create = useCreateOrganizationMutation();
  const navigate = useNavigate();
  const clear = useAuthStore((s) => s.clear);
  const error = create.error instanceof ApiError ? create.error.message : create.error ? 'Organizasyon oluşturulamadı' : null;

  const submit = async () => {
    if (name.trim().length < 2) return;
    await create.mutateAsync(name.trim());
    navigate('/', { replace: true });
  };

  return (
    <AuthLayout
      heading={<>Kuleni kur,<br />filoyu bağla.</>}
      tagline="Hesabın hazır — devam etmek için bir organizasyon kur. Sonra proje ekleyip runner ve ajan bağlantılarını tanımlayabilirsin."
    >
      <div style={{ marginBottom: 'var(--space-4)' }}>
        <InvitationsPanel onAccepted={() => navigate('/', { replace: true })} />
      </div>
      <Panel kicker="ADIM 1/1" title="Organizasyon oluştur">
        {error && <p className="k-sub k-err" style={{ marginBottom: 'var(--space-3)' }}>{error}</p>}
        <Field label="ORGANİZASYON ADI">
          <input
            className="k-input mono"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="Atlas Fintek"
            onKeyDown={(e) => e.key === 'Enter' && submit()}
            autoFocus
          />
        </Field>
        <Btn kind="pri" icon="arrow" className="k-btn-block" disabled={name.trim().length < 2 || create.isPending} onClick={submit}>
          {create.isPending ? 'OLUŞTURULUYOR…' : 'Kuleyi kur'}
        </Btn>
        <button className="k-link mono" style={{ marginTop: 'var(--space-3)', display: 'block' }} onClick={() => clear()}>
          farklı hesapla gir →
        </button>
      </Panel>
    </AuthLayout>
  );
}
