import { fireEvent, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { renderWithProviders } from '../../test/renderWithProviders';
import { useAuthStore } from '../../shared/auth/authStore';
import { profileApi } from './api';
import ProfilePage from './ProfilePage';

vi.mock('./api', () => ({
  profileApi: { updateDisplayName: vi.fn(), changePassword: vi.fn() },
}));

/** Profil sayfası: görünen ad güncelleme ve parola değiştirme akışları. */
describe('ProfilePage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useAuthStore.setState({ user: { id: 'u1', email: 'dev@test.com', displayName: 'Eski İsim' } });
  });

  it('mevcut kullanici bilgilerini formda gosterir', () => {
    renderWithProviders(<ProfilePage />);

    expect(screen.getByDisplayValue('dev@test.com')).toBeInTheDocument();
    expect(screen.getByDisplayValue('Eski İsim')).toBeInTheDocument();
  });

  it('kaydet tiklaninca trim edilmis displayName ile updateDisplayName cagirir', async () => {
    vi.mocked(profileApi.updateDisplayName).mockResolvedValue({ id: 'u1', email: 'dev@test.com', displayName: 'Yeni İsim' });

    renderWithProviders(<ProfilePage />);
    fireEvent.change(screen.getByPlaceholderText('Deniz Aksoy'), { target: { value: '  Yeni İsim  ' } });
    fireEvent.click(screen.getByText('Kaydet'));

    await waitFor(() => expect(profileApi.updateDisplayName).toHaveBeenCalledWith('Yeni İsim'));
  });

  it('gecerli sifre degisikligi formu changePassword cagirir ve alanlari temizler', async () => {
    vi.mocked(profileApi.changePassword).mockResolvedValue(undefined);

    renderWithProviders(<ProfilePage />);
    const [currentPw, newPw, confirmPw] = screen.getAllByPlaceholderText('••••••••');
    fireEvent.change(currentPw, { target: { value: 'eskiSifre123' } });
    fireEvent.change(newPw, { target: { value: 'yeniSifre123' } });
    fireEvent.change(confirmPw, { target: { value: 'yeniSifre123' } });

    fireEvent.click(screen.getByText('Şifreyi değiştir'));

    await waitFor(() =>
      expect(profileApi.changePassword).toHaveBeenCalledWith('eskiSifre123', 'yeniSifre123'),
    );
    await waitFor(() => expect(currentPw).toHaveValue(''));
  });

  it('yeni sifreler eslesmezse degistir butonu disabled kalir, API cagrilmaz', async () => {
    renderWithProviders(<ProfilePage />);
    const [currentPw, newPw, confirmPw] = screen.getAllByPlaceholderText('••••••••');
    fireEvent.change(currentPw, { target: { value: 'eskiSifre123' } });
    fireEvent.change(newPw, { target: { value: 'yeniSifre123' } });
    fireEvent.change(confirmPw, { target: { value: 'farkli-sifre' } });

    expect(screen.getByText('şifreler eşleşmiyor')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Şifreyi değiştir' })).toBeDisabled();
    expect(profileApi.changePassword).not.toHaveBeenCalled();
  });
});
