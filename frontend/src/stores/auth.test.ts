import { setActivePinia, createPinia } from 'pinia';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { useAuthStore } from './auth';
import { api } from '../api/client';

vi.mock('../api/client', () => ({
  api: {
    post: vi.fn(),
    get: vi.fn(),
  },
}));

describe('auth store', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
  });

  it('login fetches /auth/me and populates me', async () => {
    (api.post as any).mockResolvedValue({ data: { forceChangePw: false } });
    (api.get as any).mockResolvedValue({
      data: {
        userId: 'admin',
        fullName: '관리자',
        email: 'a@x',
        department: 'IT',
        roles: ['ADMIN'],
      },
    });

    const auth = useAuthStore();
    await auth.login('admin', 'pw');

    expect(auth.me).toEqual({
      userId: 'admin',
      fullName: '관리자',
      email: 'a@x',
      department: 'IT',
      roles: ['ADMIN'],
    });
    expect(auth.isAuthenticated()).toBe(true);
  });

  it('logout clears me and forceChangePw', async () => {
    (api.post as any).mockResolvedValue({});
    const auth = useAuthStore();
    (auth as any).me = { userId: 'a', fullName: 'a', email: 'a', department: 'a', roles: [] };
    await auth.logout();
    expect(auth.me).toBeNull();
    expect(auth.forceChangePw).toBe(false);
  });
});
