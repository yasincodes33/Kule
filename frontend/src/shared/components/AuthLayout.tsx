import type { ReactNode } from 'react';
import { Logo } from './Logo';

/**
 * Giriş/kayıt/onboarding/şifre sıfırlama ekranlarının ortak iki-kolonlu kabuğu. Ayrı bir
 * bileşen olarak çıkarıldı çünkü bu markup elle 2 kez kopyalanmıştı (LoginPage, CreateOrgPage)
 * ve ikisinde de aynı gerçek hata oluştu: `.k-auth-form`'un kapanış etiketi yanlış yerde olup
 * `.k-auth-brand`'in içine iç içe geçmesi — grid'in ikinci kolonu hiç yerleşmiyor, geniş
 * ekranlarda sağda boş alan kalıyordu. Tek bir bileşende doğru kurulmuş hiyerarşi, bu hatanın
 * yeni bir sayfa eklendikçe tekrarlanmasını engelliyor.
 */
export function AuthLayout({
  heading,
  tagline,
  brandExtra,
  children,
}: {
  heading: ReactNode;
  tagline: ReactNode;
  brandExtra?: ReactNode;
  children: ReactNode;
}) {
  return (
    <div className="k-auth">
      <div className="k-auth-brand">
        <div className="k-sweep" />
        <div className="k-auth-brand-in">
          <div className="k-logo">
            <Logo />
          </div>
          <h1 className="k-auth-h">{heading}</h1>
          <p className="k-auth-p">{tagline}</p>
          {brandExtra}
        </div>
      </div>
      <div className="k-auth-form">
        <div className="k-auth-form-in">{children}</div>
      </div>
    </div>
  );
}
