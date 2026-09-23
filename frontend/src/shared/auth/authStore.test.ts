import { beforeEach, describe, expect, it } from 'vitest';
import { useAuthStore } from './authStore';

const org = (id: string, name = id) => ({ id, name, slug: name });

describe('authStore.setOrganizations', () => {
  beforeEach(() => {
    useAuthStore.setState({
      accessToken: null,
      user: null,
      organizations: [],
      activeOrgId: null,
      bootstrapped: false,
    });
  });

  it('activeOrgId boşken listedeki ilk organizasyonu aktif yapar', () => {
    useAuthStore.getState().setOrganizations([org('a'), org('b')]);
    expect(useAuthStore.getState().activeOrgId).toBe('a');
  });

  it('mevcut activeOrgId yeni listede hâlâ varsa DEĞİŞTİRMEZ', () => {
    useAuthStore.setState({ activeOrgId: 'b' });
    useAuthStore.getState().setOrganizations([org('a'), org('b')]);
    expect(useAuthStore.getState().activeOrgId).toBe('b');
  });

  it('mevcut activeOrgId yeni listede yoksa ilk organizasyona düşer', () => {
    useAuthStore.setState({ activeOrgId: 'silinmis-org' });
    useAuthStore.getState().setOrganizations([org('a'), org('b')]);
    expect(useAuthStore.getState().activeOrgId).toBe('a');
  });

  it('boş liste için activeOrgId null olur', () => {
    useAuthStore.setState({ activeOrgId: 'a' });
    useAuthStore.getState().setOrganizations([]);
    expect(useAuthStore.getState().activeOrgId).toBeNull();
  });
});

describe('authStore.clear', () => {
  it('accessToken/user/organizations/activeOrgId sıfırlar', () => {
    useAuthStore.setState({
      accessToken: 'x',
      user: { id: '1', email: 'a@b.com', displayName: '' },
      organizations: [org('a')],
      activeOrgId: 'a',
    });

    useAuthStore.getState().clear();

    const state = useAuthStore.getState();
    expect(state.accessToken).toBeNull();
    expect(state.user).toBeNull();
    expect(state.organizations).toEqual([]);
    expect(state.activeOrgId).toBeNull();
  });
});

describe('authStore.setSession', () => {
  it('accessToken alanını AuthResponse\'tan doldurur', () => {
    useAuthStore.getState().setSession({ accessToken: 'acc', expiresIn: 3600 });

    const state = useAuthStore.getState();
    expect(state.accessToken).toBe('acc');
  });
});
