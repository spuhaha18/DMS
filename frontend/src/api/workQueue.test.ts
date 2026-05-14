import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  listWorkQueue,
  fetchWorkQueueCounts,
  markWorkQueueItemDone,
  cancelWorkQueueItem,
} from './workQueue';
import type { WorkQueueItem, WorkQueueCounts } from './workQueue';
import { api } from './client';

vi.mock('./client', () => ({
  api: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
  },
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

describe('workQueue api', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });
  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe('listWorkQueue', () => {
    it('returns list response on success', async () => {
      (api.get as any).mockResolvedValue({ data: { content: [mockItem], totalElements: 1 } });
      const result = await listWorkQueue();
      expect(result.content).toEqual([mockItem]);
      expect(result.totalElements).toBe(1);
      expect(api.get).toHaveBeenCalledWith('/work-queue', expect.objectContaining({ params: {} }));
    });

    it('passes kind and state params', async () => {
      (api.get as any).mockResolvedValue({ data: { content: [], totalElements: 0 } });
      await listWorkQueue({ kind: 'APPROVAL', state: 'OPEN' });
      expect(api.get).toHaveBeenCalledWith('/work-queue', expect.objectContaining({
        params: { kind: 'APPROVAL', state: 'OPEN' },
      }));
    });

    it('throws on network error', async () => {
      (api.get as any).mockRejectedValue(new Error('Network Error'));
      await expect(listWorkQueue()).rejects.toThrow('Network Error');
    });
  });

  describe('fetchWorkQueueCounts', () => {
    it('returns counts on success', async () => {
      (api.get as any).mockResolvedValue({ data: mockCounts });
      const result = await fetchWorkQueueCounts();
      expect(result).toEqual(mockCounts);
      expect(api.get).toHaveBeenCalledWith('/work-queue/counts', expect.any(Object));
    });

    it('throws on server error', async () => {
      (api.get as any).mockRejectedValue(new Error('500'));
      await expect(fetchWorkQueueCounts()).rejects.toThrow('500');
    });
  });

  describe('markWorkQueueItemDone', () => {
    it('posts to done endpoint', async () => {
      (api.post as any).mockResolvedValue({ data: undefined });
      await markWorkQueueItemDone(1);
      expect(api.post).toHaveBeenCalledWith('/work-queue/1/done', {}, expect.any(Object));
    });

    it('throws on error', async () => {
      (api.post as any).mockRejectedValue(new Error('Forbidden'));
      await expect(markWorkQueueItemDone(1)).rejects.toThrow('Forbidden');
    });
  });

  describe('cancelWorkQueueItem', () => {
    it('posts to cancel endpoint', async () => {
      (api.post as any).mockResolvedValue({ data: undefined });
      await cancelWorkQueueItem(2);
      expect(api.post).toHaveBeenCalledWith('/work-queue/2/cancel', {}, expect.any(Object));
    });

    it('throws on error', async () => {
      (api.post as any).mockRejectedValue(new Error('Not Found'));
      await expect(cancelWorkQueueItem(99)).rejects.toThrow('Not Found');
    });
  });
});
