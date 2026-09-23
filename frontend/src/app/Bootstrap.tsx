import { useEffect, type ReactNode } from 'react';
import { authApi } from '../features/auth/api';
import { Logo } from '../shared/components/Logo';
import { useAuthStore } from '../shared/auth/authStore';

/**
 * Sayfa yenilendiğinde accessToken bellekte kaybolur (bkz. authStore.ts) — refreshToken artık
 * httpOnly bir cookie'de olduğu için JS'ten hiç okunamıyor (bkz. shared/auth/authStore.ts), bu
 * yüzden burada varlığı önceden kontrol edilemez: /auth/refresh her zaman denenir, tarayıcı
 * cookie'yi (varsa) otomatik ekler. Cookie yoksa/geçersizse backend 401 döner ve doğrudan
 * /login'e düşülür (RequireAuth üzerinden).
 */
export default function Bootstrap({ children }: { children: ReactNode }) {
  const { accessToken, bootstrapped, setSession, setBootstrapped, clear } = useAuthStore();

  useEffect(() => {
    if (bootstrapped) return;
    if (accessToken) {
      setBootstrapped(true);
      return;
    }
    authApi
      .refresh()
      .then(setSession)
      .catch(() => clear())
      .finally(() => setBootstrapped(true));
  }, [bootstrapped, accessToken, setSession, setBootstrapped, clear]);

  if (!bootstrapped) {
    return (
      <div className="k-auth" style={{ gridTemplateColumns: '1fr', alignItems: 'center', justifyItems: 'center' }}>
        <div className="k-logo">
          <Logo />
        </div>
      </div>
    );
  }

  return <>{children}</>;
}
