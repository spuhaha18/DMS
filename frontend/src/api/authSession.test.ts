import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { fetchSessionState } from './authSession';
import type { SessionState } from './authSession';
import { api } from './client';

vi.mock('./client', () => ({
  api: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
  },
}));

const mockSession: SessionState = {
  userId: 'user-001',
  userName: '홍길동',
  firstSignRequired: false,
};

describe('authSession api', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });
  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe('fetchSessionState', () => {
    it('returns session state on success', async () => {
      (api.get as any).mockResolvedValue({ data: mockSession });
      const result = await fetchSessionState();
      expect(result).toEqual(mockSession);
      expect(api.get).toHaveBeenCalledWith('/auth/session-state', expect.any(Object));
    });

    it('returns firstSignRequired true when needed', async () => {
      const sessionWithSign: SessionState = { ...mockSession, firstSignRequired: true };
      (api.get as any).mockResolvedValue({ data: sessionWithSign });
      const result = await fetchSessionState();
      expect(result.firstSignRequired).toBe(true);
    });

    it('throws on unauthenticated error', async () => {
      (api.get as any).mockRejectedValue(new Error('401 Unauthorized'));
      await expect(fetchSessionState()).rejects.toThrow('401 Unauthorized');
    });
  });
});
