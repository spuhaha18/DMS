<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue';
import { useRouter } from 'vue-router';
import { useWorkQueueStore } from '../../stores/workQueue';

const store = useWorkQueueStore();
const router = useRouter();

onMounted(() => {
  store.startPolling(30000);
});

onUnmounted(() => {
  store.stopPolling();
});

function navigate() {
  router.push('/work-queue');
}
</script>

<template>
  <button
    type="button"
    class="wq-icon-btn"
    :aria-label="`할 일 ${store.counts.open}건`"
    @click="navigate"
  >
    <!-- Inbox/tray icon SVG -->
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
      <polyline points="22 12 16 12 14 15 10 15 8 12 2 12"/>
      <path d="M5.45 5.11L2 12v6a2 2 0 002 2h16a2 2 0 002-2v-6l-3.45-6.89A2 2 0 0016.76 4H7.24a2 2 0 00-1.79 1.11z"/>
    </svg>
    <span
      v-if="store.counts.open > 0"
      class="badge"
      aria-hidden="true"
    >{{ store.counts.open > 99 ? '99+' : store.counts.open }}</span>
  </button>
</template>

<style scoped>
.wq-icon-btn {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px; height: 36px;
  border: none; background: transparent; cursor: pointer;
  color: #6b7280; border-radius: 0.375rem;
}
.wq-icon-btn:hover { color: #1a56db; background: #eff6ff; }

.badge {
  position: absolute;
  top: 2px; right: 2px;
  min-width: 16px; height: 16px;
  padding: 0 3px;
  background: #ef4444; color: white;
  font-size: 10px; font-weight: 700;
  border-radius: 8px;
  display: flex; align-items: center; justify-content: center;
}
</style>
