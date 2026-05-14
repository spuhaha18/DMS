import { setActivePinia, createPinia } from 'pinia';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useNotificationStore } from './notification';
import * as notificationApi from '../api/notifications';
import type { Notification } from '../api/notifications';

vi.mock('../api/notifications', () => ({
  listNotifications: vi.fn(),
  fetchUnreadCount: vi.fn(),
  markNotificationRead: vi.fn(),
}));

const mockNotification: Notification = {
  id: 1,
  eventCode: 'DOC_APPROVED',
  title: '문서 승인 완료',
  read: false,
  severity: 'INFO',
  createdAt: '2026-05-01T00:00:00Z',
};

describe('notification store', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
  });
  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe('fetchList', () => {
    it('sets items from api response', async () => {
      (notificationApi.listNotifications as any).mockResolvedValue({ content: [mockNotification], totalElements: 1 });
      const store = useNotificationStore();
      await store.fetchList();
      expect(store.items).toEqual([mockNotification]);
      expect(store.isLoading).toBe(false);
    });

    it('passes page and size params', async () => {
      (notificationApi.listNotifications as any).mockResolvedValue({ content: [], totalElements: 0 });
      const store = useNotificationStore();
      await store.fetchList({ page: 2, size: 20 });
      expect(notificationApi.listNotifications).toHaveBeenCalledWith({ page: 2, size: 20 });
    });

    it('clears loading on failure', async () => {
      (notificationApi.listNotifications as any).mockRejectedValue(new Error('Error'));
      const store = useNotificationStore();
      await expect(store.fetchList()).rejects.toThrow('Error');
      expect(store.isLoading).toBe(false);
    });
  });

  describe('fetchUnreadCount', () => {
    it('sets unread count', async () => {
      (notificationApi.fetchUnreadCount as any).mockResolvedValue({ count: 7 });
      const store = useNotificationStore();
      await store.fetchUnreadCount();
      expect(store.unread).toBe(7);
    });
  });

  describe('markRead', () => {
    it('calls markNotificationRead and updates item.read', async () => {
      (notificationApi.listNotifications as any).mockResolvedValue({ content: [mockNotification], totalElements: 1 });
      (notificationApi.markNotificationRead as any).mockResolvedValue(undefined);
      (notificationApi.fetchUnreadCount as any).mockResolvedValue({ count: 0 });
      const store = useNotificationStore();
      await store.fetchList();
      await store.markRead(1);
      expect(notificationApi.markNotificationRead).toHaveBeenCalledWith(1);
      expect(store.items[0].read).toBe(true);
      expect(store.unread).toBe(0);
    });

    it('throws on api error', async () => {
      (notificationApi.markNotificationRead as any).mockRejectedValue(new Error('Not Found'));
      const store = useNotificationStore();
      await expect(store.markRead(99)).rejects.toThrow('Not Found');
    });
  });

  describe('startPolling / stopPolling', () => {
    it('calls fetchUnreadCount immediately and on interval', () => {
      vi.useFakeTimers();
      (notificationApi.fetchUnreadCount as any).mockResolvedValue({ count: 3 });
      const store = useNotificationStore();
      store.startPolling(5000);
      expect(notificationApi.fetchUnreadCount).toHaveBeenCalledTimes(1);
      vi.advanceTimersByTime(5000);
      expect(notificationApi.fetchUnreadCount).toHaveBeenCalledTimes(2);
      store.stopPolling();
      vi.useRealTimers();
    });

    it('stopPolling prevents further calls', () => {
      vi.useFakeTimers();
      (notificationApi.fetchUnreadCount as any).mockResolvedValue({ count: 0 });
      const store = useNotificationStore();
      store.startPolling(5000);
      store.stopPolling();
      vi.advanceTimersByTime(10000);
      expect(notificationApi.fetchUnreadCount).toHaveBeenCalledTimes(1);
      vi.useRealTimers();
    });
  });
});
