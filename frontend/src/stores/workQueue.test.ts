import { setActivePinia, createPinia } from 'pinia';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useWorkQueueStore } from './workQueue';
import * as workQueueApi from '../api/workQueue';
import type { WorkQueueItem, WorkQueueCounts } from '../api/workQueue';

vi.mock('../api/workQueue', () => ({
  listWorkQueue: vi.fn(),
  fetchWorkQueueCounts: vi.fn(),
  markWorkQueueItemDone: vi.fn(),
  cancelWorkQueueItem: vi.fn(),
}));

const mockItem: WorkQueueItem = {
  id: 1,
  kind: 'APPROVAL',
  state: 'OPEN',
  title: '승인 요청',
  priority: 'HIGH',
  assigneeUserId: 42,
  createdAt: '2026-05-01T00:00:00Z',
};

const mockCounts: WorkQueueCounts = { open: 3, total: 10 };

describe('workQueue store', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
  });
  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe('fetchList', () => {
    it('sets items from api response', async () => {
      (workQueueApi.listWorkQueue as any).mockResolvedValue({ content: [mockItem], totalElements: 1 });
      const store = useWorkQueueStore();
      await store.fetchList();
      expect(store.items).toEqual([mockItem]);
      expect(store.isLoading).toBe(false);
    });

    it('passes params to api', async () => {
      (workQueueApi.listWorkQueue as any).mockResolvedValue({ content: [], totalElements: 0 });
      const store = useWorkQueueStore();
      await store.fetchList({ kind: 'TRAINING', state: 'OPEN' });
      expect(workQueueApi.listWorkQueue).toHaveBeenCalledWith({ kind: 'TRAINING', state: 'OPEN' });
    });

    it('sets error on failure', async () => {
      (workQueueApi.listWorkQueue as any).mockRejectedValue(new Error('Server Error'));
      const store = useWorkQueueStore();
      await store.fetchList();
      expect(store.error).toBe('Server Error');
      expect(store.isLoading).toBe(false);
    });
  });

  describe('fetchCounts', () => {
    it('sets counts from api response', async () => {
      (workQueueApi.fetchWorkQueueCounts as any).mockResolvedValue(mockCounts);
      const store = useWorkQueueStore();
      await store.fetchCounts();
      expect(store.counts).toEqual(mockCounts);
    });

    it('sets error on failure', async () => {
      (workQueueApi.fetchWorkQueueCounts as any).mockRejectedValue(new Error('API Error'));
      const store = useWorkQueueStore();
      await store.fetchCounts();
      expect(store.error).toBe('API Error');
    });
  });

  describe('markDone', () => {
    it('calls markWorkQueueItemDone then refreshes counts', async () => {
      (workQueueApi.markWorkQueueItemDone as any).mockResolvedValue(undefined);
      (workQueueApi.fetchWorkQueueCounts as any).mockResolvedValue({ open: 2, total: 10 });
      const store = useWorkQueueStore();
      await store.markDone(1);
      expect(workQueueApi.markWorkQueueItemDone).toHaveBeenCalledWith(1);
      expect(workQueueApi.fetchWorkQueueCounts).toHaveBeenCalled();
      expect(store.counts).toEqual({ open: 2, total: 10 });
    });
  });

  describe('cancel', () => {
    it('calls cancelWorkQueueItem then refreshes counts', async () => {
      (workQueueApi.cancelWorkQueueItem as any).mockResolvedValue(undefined);
      (workQueueApi.fetchWorkQueueCounts as any).mockResolvedValue({ open: 1, total: 10 });
      const store = useWorkQueueStore();
      await store.cancel(2);
      expect(workQueueApi.cancelWorkQueueItem).toHaveBeenCalledWith(2);
      expect(workQueueApi.fetchWorkQueueCounts).toHaveBeenCalled();
    });
  });

  describe('startPolling / stopPolling', () => {
    it('calls fetchCounts immediately and sets interval', () => {
      vi.useFakeTimers();
      (workQueueApi.fetchWorkQueueCounts as any).mockResolvedValue(mockCounts);
      const store = useWorkQueueStore();
      store.startPolling(5000);
      expect(workQueueApi.fetchWorkQueueCounts).toHaveBeenCalledTimes(1);
      vi.advanceTimersByTime(5000);
      expect(workQueueApi.fetchWorkQueueCounts).toHaveBeenCalledTimes(2);
      store.stopPolling();
      vi.useRealTimers();
    });

    it('stopPolling clears the interval', () => {
      vi.useFakeTimers();
      (workQueueApi.fetchWorkQueueCounts as any).mockResolvedValue(mockCounts);
      const store = useWorkQueueStore();
      store.startPolling(5000);
      store.stopPolling();
      vi.advanceTimersByTime(10000);
      // Only the initial call, no additional calls after stop
      expect(workQueueApi.fetchWorkQueueCounts).toHaveBeenCalledTimes(1);
      vi.useRealTimers();
    });
  });
});
