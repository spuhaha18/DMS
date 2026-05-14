import { defineStore } from 'pinia';
import { ref } from 'vue';
import {
  listWorkQueue,
  fetchWorkQueueCounts,
  markWorkQueueItemDone,
  cancelWorkQueueItem,
} from '../api/workQueue';
import type { WorkQueueItem, WorkQueueCounts } from '../api/workQueue';

export const useWorkQueueStore = defineStore('workQueue', () => {
  const items = ref<WorkQueueItem[]>([]);
  const counts = ref<WorkQueueCounts>({ open: 0, total: 0 });
  const isLoading = ref(false);
  const error = ref<string | null>(null);
  let pollingTimer: ReturnType<typeof setInterval> | null = null;

  async function fetchList(params?: { kind?: string; state?: string }) {
    isLoading.value = true;
    error.value = null;
    try {
      const response = await listWorkQueue(params ?? {});
      items.value = response.content;
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Unknown error';
    } finally {
      isLoading.value = false;
    }
  }

  async function fetchCounts() {
    try {
      counts.value = await fetchWorkQueueCounts();
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Unknown error';
    }
  }

  async function markDone(id: number) {
    await markWorkQueueItemDone(id);
    await fetchCounts();
  }

  async function cancel(id: number) {
    await cancelWorkQueueItem(id);
    await fetchCounts();
  }

  function startPolling(intervalMs = 30000) {
    stopPolling();
    fetchCounts();
    pollingTimer = setInterval(() => fetchCounts(), intervalMs);
  }

  function stopPolling() {
    if (pollingTimer !== null) {
      clearInterval(pollingTimer);
      pollingTimer = null;
    }
  }

  return { items, counts, isLoading, error, fetchList, fetchCounts, markDone, cancel, startPolling, stopPolling };
});
