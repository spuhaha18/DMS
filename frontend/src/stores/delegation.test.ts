import { setActivePinia, createPinia } from 'pinia';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useDelegationStore } from './delegation';
import * as delegationApi from '../api/delegations';
import type { Delegation, RequestDelegationBody } from '../api/delegations';

vi.mock('../api/delegations', () => ({
  requestDelegation: vi.fn(),
  listDelegations: vi.fn(),
  approveDelegation: vi.fn(),
  rejectDelegation: vi.fn(),
  revokeDelegation: vi.fn(),
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

describe('delegation store', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
  });
  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe('fetchList', () => {
    it('sets items from api response', async () => {
      (delegationApi.listDelegations as any).mockResolvedValue([mockDelegation]);
      const store = useDelegationStore();
      await store.fetchList();
      expect(store.items).toEqual([mockDelegation]);
      expect(store.isLoading).toBe(false);
    });

    it('passes as param to api', async () => {
      (delegationApi.listDelegations as any).mockResolvedValue([]);
      const store = useDelegationStore();
      await store.fetchList('delegator');
      expect(delegationApi.listDelegations).toHaveBeenCalledWith('delegator');
    });

    it('clears loading on failure', async () => {
      (delegationApi.listDelegations as any).mockRejectedValue(new Error('Error'));
      const store = useDelegationStore();
      await expect(store.fetchList()).rejects.toThrow('Error');
      expect(store.isLoading).toBe(false);
    });
  });

  describe('request', () => {
    it('calls requestDelegation and prepends to items', async () => {
      (delegationApi.requestDelegation as any).mockResolvedValue(mockDelegation);
      const store = useDelegationStore();
      const result = await store.request(mockRequestBody);
      expect(result).toEqual(mockDelegation);
      expect(store.items[0]).toEqual(mockDelegation);
    });

    it('throws on api error', async () => {
      (delegationApi.requestDelegation as any).mockRejectedValue(new Error('Validation Error'));
      const store = useDelegationStore();
      await expect(store.request(mockRequestBody)).rejects.toThrow('Validation Error');
    });
  });

  describe('approve', () => {
    it('calls approveDelegation and updates state to APPROVED', async () => {
      (delegationApi.listDelegations as any).mockResolvedValue([mockDelegation]);
      (delegationApi.approveDelegation as any).mockResolvedValue(undefined);
      const store = useDelegationStore();
      await store.fetchList();
      await store.approve(1);
      expect(delegationApi.approveDelegation).toHaveBeenCalledWith(1);
      expect(store.items[0].state).toBe('APPROVED');
    });

    it('throws on api error', async () => {
      (delegationApi.approveDelegation as any).mockRejectedValue(new Error('Forbidden'));
      const store = useDelegationStore();
      await expect(store.approve(1)).rejects.toThrow('Forbidden');
    });
  });

  describe('reject', () => {
    it('calls rejectDelegation and updates state to REJECTED', async () => {
      (delegationApi.listDelegations as any).mockResolvedValue([mockDelegation]);
      (delegationApi.rejectDelegation as any).mockResolvedValue(undefined);
      const store = useDelegationStore();
      await store.fetchList();
      await store.reject(1, '요건 미충족');
      expect(delegationApi.rejectDelegation).toHaveBeenCalledWith(1, '요건 미충족');
      expect(store.items[0].state).toBe('REJECTED');
    });

    it('throws on api error', async () => {
      (delegationApi.rejectDelegation as any).mockRejectedValue(new Error('Not Found'));
      const store = useDelegationStore();
      await expect(store.reject(99, 'reason')).rejects.toThrow('Not Found');
    });
  });

  describe('revoke', () => {
    it('calls revokeDelegation and updates state to REVOKED', async () => {
      (delegationApi.listDelegations as any).mockResolvedValue([{ ...mockDelegation, state: 'APPROVED' as const }]);
      (delegationApi.revokeDelegation as any).mockResolvedValue(undefined);
      const store = useDelegationStore();
      await store.fetchList();
      await store.revoke(1);
      expect(delegationApi.revokeDelegation).toHaveBeenCalledWith(1);
      expect(store.items[0].state).toBe('REVOKED');
    });

    it('throws on api error', async () => {
      (delegationApi.revokeDelegation as any).mockRejectedValue(new Error('Conflict'));
      const store = useDelegationStore();
      await expect(store.revoke(1)).rejects.toThrow('Conflict');
    });
  });
});
