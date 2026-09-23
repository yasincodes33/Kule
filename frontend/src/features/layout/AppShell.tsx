import { useEffect, useState } from 'react';
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useAuthStore } from '../../shared/auth/authStore';
import { ErrorBoundary } from '../../shared/components/ErrorBoundary';
import { Icon } from '../../shared/components/Icon';
import { Logo } from '../../shared/components/Logo';
import { NotificationBell } from '../notifications/NotificationBell';
import { useOrganizationsQuery } from '../organizations/hooks';
import { useApprovalsQuery } from '../approvals/hooks';
import { authApi } from '../auth/api';
import { clock } from '../../shared/utils/format';

const NAV = [
  { to: '/', end: true, icon: 'board', label: 'Görev panosu', hint: 'AKIŞ' },
  { to: '/projects', icon: 'git', label: 'Projeler', hint: 'DEPO' },
  { to: '/approvals', icon: 'shield', label: 'Onay kuyruğu', hint: 'KAPI' },
  { to: '/runners', icon: 'server', label: 'Runner & ajan', hint: 'FİLO' },
  { to: '/organization', icon: 'users', label: 'Organizasyon', hint: 'ERİŞİM' },
  { to: '/audit-log', icon: 'activity', label: 'Denetim kaydı', hint: 'GEÇMİŞ' },
];

export default function AppShell() {
  const location = useLocation();
  const { organizations, activeOrgId, user, setActiveOrgId, clear } = useAuthStore();
  useOrganizationsQuery();
  const pendingApprovals = useApprovalsQuery().data?.length ?? 0;
  const navigate = useNavigate();
  const [orgOpen, setOrgOpen] = useState(false);
  const [now, setNow] = useState(Date.now());

  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(t);
  }, []);

  const org = organizations.find((o) => o.id === activeOrgId);
  const initials = (user?.displayName || user?.email || '??').split(/[\s@.]/).filter(Boolean).slice(0, 2).map((s) => s[0]?.toUpperCase()).join('');

  const logout = () => {
    // Backend'in artık DB'de izlediği refresh token kaydını da iptal eder ve httpOnly cookie'yi
    // temizler (bkz. AuthController.logout) — başarısız olsa bile yerel oturumu temizlemeyi engellemez.
    authApi.logout().catch(() => {});
    clear();
    navigate('/login', { replace: true });
  };

  if (!org) return null;

  return (
    <div className="k-shell">
      <aside className="k-rail">
        <div className="k-rail-brand">
          <Logo size="sm" />
        </div>
        <nav className="k-nav">
          {NAV.map((n) => (
            <NavLink key={n.to} to={n.to} end={n.end} className={({ isActive }) => 'k-nav-i' + (isActive ? ' on' : '')}>
              <Icon n={n.icon} s={16} />
              <span className="k-nav-l">{n.label}</span>
              {n.to === '/approvals' && pendingApprovals > 0 && <span className="mono k-nav-badge">{pendingApprovals}</span>}
            </NavLink>
          ))}
        </nav>
        <div className="k-rail-foot">
          <div className="k-rail-live">
            <span className="mono k-kicker">SİSTEM</span>
            <div className="k-rail-live-r mono"><span className="k-pulse k-pulse-online" />API BAĞLI</div>
            <div className="mono k-sub">{org.slug}</div>
          </div>
          <button className="k-nav-i" onClick={logout}><Icon n="logout" s={16} /><span className="k-nav-l">Çıkış</span></button>
        </div>
      </aside>

      <div className="k-main">
        <header className="k-top">
          <div className="k-org-switcher">
            <button className="k-org-btn" onClick={() => setOrgOpen(!orgOpen)}>
              <span className="k-org-mark mono sm">{org.name.slice(0, 2).toUpperCase()}</span>
              <span className="k-org-btn-t"><span className="k-org-btn-n">{org.name}</span><span className="mono k-sub">{org.slug}</span></span>
              <Icon n="chev" s={14} c="var(--ink-3)" />
            </button>
            {orgOpen && (
              <div className="k-drop">
                {organizations.map((o) => (
                  <button key={o.id} className={'k-drop-i' + (o.id === activeOrgId ? ' on' : '')} onClick={() => { setActiveOrgId(o.id); setOrgOpen(false); }}>
                    <span className="k-org-mark mono sm">{o.name.slice(0, 2).toUpperCase()}</span>
                    <span><span className="k-org-btn-n">{o.name}</span></span>
                    {o.id === activeOrgId && <Icon n="check" s={14} c="var(--color-accent)" />}
                  </button>
                ))}
                <button className="k-drop-i" onClick={() => { navigate('/organization'); setOrgOpen(false); }}>
                  <Icon n="plus" s={14} /><span>Organizasyon yönet</span>
                </button>
              </div>
            )}
          </div>
          <div className="k-top-r" style={{ marginLeft: 'auto' }}>
            <span className="mono k-clock">{clock(now)}</span>
            <NotificationBell />
            <button className="k-avatar mono" onClick={() => navigate('/profile')} title="Profil">{initials}</button>
          </div>
        </header>

        <div className="k-scroll">
          {/* Bir sayfa cokerse yalnizca ICERIK alani hata gosterir; menu/baslik ayakta kalir
              ve baska bir sayfaya gecilebilir. `key` sayfa yolu: gezinince sinir sifirlanir. */}
          <ErrorBoundary key={location.pathname}>
            <Outlet />
          </ErrorBoundary>
        </div>
      </div>
    </div>
  );
}
