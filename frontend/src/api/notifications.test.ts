import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  listNotifications,
  fetchUnreadCount,
  markNotificationRead,
} from './notifications';
import type { Notification } from './notifications';
import { api } from './client';

vi.mock('./client', () => ({
  api: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
  },
}));

const mockNotification: Notification = {
  id: 1,
  eventCode: 'DOC_APPROVED',
  title: '문서 승인 완료',
  read: false,
  severity: 'INFO',
  createdAt: '2026-05-01T00:00:00Z',
};

describe('notifications api', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });
  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe('listNotifications', () => {
    it('returns notification list on success', async () => {
      (api.get as any).mockResolvedValue({ data: { content: [mockNotification], totalElements: 1 } });
      const result = await listNotifications();
      expect(result.content).toEqual([mockNotification]);
      expect(result.totalElements).toBe(1);
      expect(api.get).toHaveBeenCalledWith('/notifications', expect.objectContaining({ params: {} }));
    });

    it('passes page and size params', async () => {
      (api.get as any).mockResolvedValue({ data: { content: [], totalElements: 0 } });
      await listNotifications({ page: 1, size: 20 });
      expect(api.get).toHaveBeenCalledWith('/notifications', expect.objectContaining({
        params: { page: 1, size: 20 },
      }));
    });

    it('throws on network error', async () => {
      (api.get as any).mockRejectedValue(new Error('Network Error'));
      await expect(listNotifications()).rejects.toThrow('Network Error');
    });
  });

  describe('fetchUnreadCount', () => {
    it('returns unread count on success', async () => {
      (api.get as any).mockResolvedValue({ data: { count: 5 } });
      const result = await fetchUnreadCount();
      expect(result.count).toBe(5);
      expect(api.get).toHaveBeenCalledWith('/notifications/unread-count', expect.any(Object));
    });

    it('throws on server error', async () => {
      (api.get as any).mockRejectedValue(new Error('503'));
      await expect(fetchUnreadCount()).rejects.toThrow('503');
    });
  });

  describe('markNotificationRead', () => {
    it('calls put on read endpoint', async () => {
      (api.put as any).mockResolvedValue({ data: undefined });
      await markNotificationRead(1);
      expect(api.put).toHaveBeenCalledWith('/notifications/1/read', {}, expect.any(Object));
    });

    it('throws on error', async () => {
      (api.put as any).mockRejectedValue(new Error('Not Found'));
      await expect(markNotificationRead(99)).rejects.toThrow('Not Found');
    });
  });
});
