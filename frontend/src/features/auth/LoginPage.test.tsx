import { fireEvent, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { renderWithProviders } from '../../test/renderWithProviders';
import { useAuthStore } from '../../shared/auth/authStore';
import { ApiError } from '../../shared/api/client';
import { authApi } from './api';
import LoginPage from './LoginPage';

vi.mock('./api', () => ({
  authApi: { login: vi.fn(), register: vi.fn() },
}));

/**
 * Uygulamanın tek giriş noktası olan form: giriş ve kayıt, tek bileşende iki mod.
 */
describe('LoginPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useAuthStore.setState({ accessToken: null, organizations: [], activeOrgId: null, user: null });
  });

  it('gecersiz e-posta ile giris butonu devre disi kalir', () => {
    renderWithProviders(<LoginPage />);

    fireEvent.change(screen.getByPlaceholderText('deniz@sirket.com'), { target: { value: 'gecersiz-eposta' } });
    fireEvent.change(screen.getByPlaceholderText('••••••••'), { target: { value: 'sifre123' } });

    expect(screen.getByRole('button', { name: 'Kuleye gir' })).toBeDisabled();
  });

  it('gecerli girisle login cagirir, session i set eder ve anasayfaya yonlendirir', async () => {
    vi.mocked(authApi.login).mockResolvedValue({ accessToken: 'acc-token', expiresIn: 3600 });

    renderWithProviders(<LoginPage />);
    fireEvent.change(screen.getByPlaceholderText('deniz@sirket.com'), { target: { value: 'dev@test.com' } });
    fireEvent.change(screen.getByPlaceholderText('••••••••'), { target: { value: 'sifre123' } });
    fireEvent.click(screen.getByRole('button', { name: 'Kuleye gir' }));

    await waitFor(() => expect(authApi.login).toHaveBeenCalledWith('dev@test.com', 'sifre123'));
    await waitFor(() => expect(useAuthStore.getState().accessToken).toBe('acc-token'));
  });

  it('login basarisiz olursa ApiError mesaji gosterilir, session degismez', async () => {
    vi.mocked(authApi.login).mockRejectedValue(
      new ApiError({ status: 401, message: 'E-posta veya şifre hatalı', timestamp: '', fieldErrors: null }),
    );

    renderWithProviders(<LoginPage />);
    fireEvent.change(screen.getByPlaceholderText('deniz@sirket.com'), { target: { value: 'dev@test.com' } });
    fireEvent.change(screen.getByPlaceholderText('••••••••'), { target: { value: 'yanlisSifre' } });
    fireEvent.click(screen.getByRole('button', { name: 'Kuleye gir' }));

    expect(await screen.findByText('E-posta veya şifre hatalı')).toBeInTheDocument();
    expect(useAuthStore.getState().accessToken).toBeNull();
  });

  it('KAYIT sekmesine gecince 8 karakterden kisa sifre ile buton devre disi kalir', () => {
    renderWithProviders(<LoginPage />);

    fireEvent.click(screen.getByText('KAYIT'));
    fireEvent.change(screen.getByPlaceholderText('deniz@sirket.com'), { target: { value: 'dev@test.com' } });
    fireEvent.change(screen.getByPlaceholderText('••••••••'), { target: { value: 'kisa' } });

    expect(screen.getByRole('button', { name: 'Hesap oluştur' })).toBeDisabled();
  });

  it('KAYIT sekmesinde gecerli bilgilerle register cagirir', async () => {
    vi.mocked(authApi.register).mockResolvedValue({ accessToken: 'acc-token-2', expiresIn: 3600 });

    renderWithProviders(<LoginPage />);
    fireEvent.click(screen.getByText('KAYIT'));
    fireEvent.change(screen.getByPlaceholderText('Deniz Aksoy'), { target: { value: 'Test Kullanıcı' } });
    fireEvent.change(screen.getByPlaceholderText('deniz@sirket.com'), { target: { value: 'yeni@test.com' } });
    fireEvent.change(screen.getByPlaceholderText('••••••••'), { target: { value: 'gecerlisifre123' } });
    fireEvent.click(screen.getByRole('button', { name: 'Hesap oluştur' }));

    await waitFor(() =>
      expect(authApi.register).toHaveBeenCalledWith('yeni@test.com', 'gecerlisifre123', 'Test Kullanıcı'),
    );
  });
});
