import { defineStore } from 'pinia';
import { ref } from 'vue';
import {
  listNotifications,
  fetchUnreadCount,
  markNotificationRead,
} from '../api/notifications';
import type { Notification } from '../api/notifications';

export const useNotificationStore = defineStore('notification', () => {
  const items = ref<Notification[]>([]);
  const unread = ref(0);
  const isLoading = ref(false);
  const error = ref<string | null>(null);
  let pollingTimer: ReturnType<typeof setInterval> | null = null;

  async function fetchList(params?: { page?: number; size?: number }) {
    isLoading.value = true;
    error.value = null;
    try {
      const response = await listNotifications(params ?? {});
      items.value = response.content;
    } catch {
      error.value = '알림을 불러오지 못했습니다.';
    } finally {
      isLoading.value = false;
    }
  }

  async function fetchUnreadCountAction() {
    const response = await fetchUnreadCount();
    unread.value = response.count;
  }

  async function markRead(id: number) {
    await markNotificationRead(id);
    const target = items.value.find((n) => n.id === id);
    if (target) {
      target.read = true;
    }
    await fetchUnreadCountAction();
  }

  function startPolling(intervalMs = 30000) {
    stopPolling();
    fetchUnreadCountAction();
    pollingTimer = setInterval(() => fetchUnreadCountAction(), intervalMs);
  }

  function stopPolling() {
    if (pollingTimer !== null) {
      clearInterval(pollingTimer);
      pollingTimer = null;
    }
  }

  return { items, unread, error, isLoading, fetchList, fetchUnreadCount: fetchUnreadCountAction, markRead, startPolling, stopPolling };
});
