<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue';
import { useRouter } from 'vue-router';
import { useNotificationStore } from '../../stores/notification';

const store = useNotificationStore();
const router = useRouter();

onMounted(() => {
  store.startPolling(30000);
});

onUnmounted(() => {
  store.stopPolling();
});

function navigate() {
  router.push('/notifications');
}
</script>

<template>
  <button
    type="button"
    class="notif-icon-btn"
    :aria-label="store.unread > 0 ? `알림 ${store.unread}건` : '알림'"
    @click="navigate"
  >
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
      <path d="M18 8A6 6 0 006 8c0 7-3 9-3 9h18s-3-2-3-9"/>
      <path d="M13.73 21a2 2 0 01-3.46 0"/>
    </svg>
    <span
      v-if="store.unread > 0"
      class="badge"
      aria-hidden="true"
    >{{ store.unread > 99 ? '99+' : store.unread }}</span>
  </button>
</template>

<style scoped>
.notif-icon-btn {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px; height: 36px;
  border: none; background: transparent; cursor: pointer;
  color: #6b7280; border-radius: 0.375rem;
}
.notif-icon-btn:hover { color: #1a56db; background: #eff6ff; }

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
