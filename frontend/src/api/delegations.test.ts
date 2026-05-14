import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  requestDelegation,
  listDelegations,
  approveDelegation,
  rejectDelegation,
  revokeDelegation,
} from './delegations';
import type { Delegation, RequestDelegationBody } from './delegations';
import { api } from './client';

vi.mock('./client', () => ({
  api: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
  },
}));

const mockDelegation: Delegation = {
  id: 1,
  delegatorUserId: 10,
  delegateUserId: 20,
  scopeKind: 'ALL',
  reason: '출장으로 인한 권한 위임',
  validFrom: '2026-05-01',
  validTo: '2026-05-31',
  state: 'REQUESTED',
  createdAt: '2026-04-30T00:00:00Z',
};

const mockRequestBody: RequestDelegationBody = {
  delegateUserId: 20,
  scopeKind: 'ALL',
  reason: '출장으로 인한 권한 위임',
  validFrom: '2026-05-01',
  validTo: '2026-05-31',
};

describe('delegations api', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });
  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe('requestDelegation', () => {
    it('posts body and returns delegation', async () => {
      (api.post as any).mockResolvedValue({ data: mockDelegation });
      const result = await requestDelegation(mockRequestBody);
      expect(result).toEqual(mockDelegation);
      expect(api.post).toHaveBeenCalledWith('/delegations', mockRequestBody, expect.any(Object));
    });

    it('throws on validation error', async () => {
      (api.post as any).mockRejectedValue(new Error('400 Bad Request'));
      await expect(requestDelegation(mockRequestBody)).rejects.toThrow('400 Bad Request');
    });
  });

  describe('listDelegations', () => {
    it('returns delegation list without filter', async () => {
      (api.get as any).mockResolvedValue({ data: [mockDelegation] });
      const result = await listDelegations();
      expect(result).toEqual([mockDelegation]);
      expect(api.get).toHaveBeenCalledWith('/delegations', expect.objectContaining({ params: {} }));
    });

    it('passes as=delegator param', async () => {
      (api.get as any).mockResolvedValue({ data: [mockDelegation] });
      await listDelegations('delegator');
      expect(api.get).toHaveBeenCalledWith('/delegations', expect.objectContaining({
        params: { as: 'delegator' },
      }));
    });

    it('passes as=qa-pending param', async () => {
      (api.get as any).mockResolvedValue({ data: [] });
      await listDelegations('qa-pending');
      expect(api.get).toHaveBeenCalledWith('/delegations', expect.objectContaining({
        params: { as: 'qa-pending' },
      }));
    });

    it('throws on network error', async () => {
      (api.get as any).mockRejectedValue(new Error('Network Error'));
      await expect(listDelegations()).rejects.toThrow('Network Error');
    });
  });

  describe('approveDelegation', () => {
    it('posts to approve endpoint', async () => {
      (api.post as any).mockResolvedValue({ data: undefined });
      await approveDelegation(1);
      expect(api.post).toHaveBeenCalledWith('/delegations/1/approve', {}, expect.any(Object));
    });

    it('throws on forbidden error', async () => {
      (api.post as any).mockRejectedValue(new Error('403 Forbidden'));
      await expect(approveDelegation(1)).rejects.toThrow('403 Forbidden');
    });
  });

  describe('rejectDelegation', () => {
    it('posts reason to reject endpoint', async () => {
      (api.post as any).mockResolvedValue({ data: undefined });
      await rejectDelegation(1, '요건 미충족');
      expect(api.post).toHaveBeenCalledWith('/delegations/1/reject', { reason: '요건 미충족' }, expect.any(Object));
    });

    it('throws on error', async () => {
      (api.post as any).mockRejectedValue(new Error('Not Found'));
      await expect(rejectDelegation(99, 'reason')).rejects.toThrow('Not Found');
    });
  });

  describe('revokeDelegation', () => {
    it('posts to revoke endpoint', async () => {
      (api.post as any).mockResolvedValue({ data: undefined });
      await revokeDelegation(1);
      expect(api.post).toHaveBeenCalledWith('/delegations/1/revoke', {}, expect.any(Object));
    });

    it('throws on error', async () => {
      (api.post as any).mockRejectedValue(new Error('Conflict'));
      await expect(revokeDelegation(1)).rejects.toThrow('Conflict');
    });
  });
});
