<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref } from 'vue';
import type { ToastType } from './useToast';

interface Props {
  message: string;
  type?: ToastType;
  duration?: number;
}

const props = withDefaults(defineProps<Props>(), {
  type: 'info',
  duration: 4000,
});

const emit = defineEmits<{ (e: 'dismiss'): void }>();

const visible = ref(true);
let timer: ReturnType<typeof setTimeout> | null = null;

onMounted(() => {
  if (props.duration > 0) {
    timer = setTimeout(() => {
      visible.value = false;
      emit('dismiss');
    }, props.duration);
  }
});

onBeforeUnmount(() => {
  if (timer) clearTimeout(timer);
});

function close() {
  if (timer) clearTimeout(timer);
  visible.value = false;
  emit('dismiss');
}
</script>

<template>
  <div
    v-if="visible"
    class="toast"
    :class="`toast-${props.type}`"
    role="status"
    :aria-live="props.type === 'error' ? 'assertive' : 'polite'"
  >
    <span class="toast-msg">{{ props.message }}</span>
    <button type="button" class="toast-close" aria-label="닫기" @click="close">×</button>
  </div>
</template>

<style scoped>
.toast {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  min-width: 280px;
  max-width: 480px;
  padding: 0.75rem 1rem;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-left-width: 4px;
  border-radius: var(--radius-base, 0.375rem);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  font-family: var(--font-base, 'Inter', system-ui, sans-serif);
  font-size: 0.875rem;
  color: #111827;
}
.toast-success { border-left-color: #16a34a; }
.toast-error   { border-left-color: #dc2626; }
.toast-warning { border-left-color: #d97706; }
.toast-info    { border-left-color: var(--color-primary, #1a56db); }
.toast-msg { flex: 1; white-space: pre-line; }
.toast-close {
  background: none;
  border: none;
  font-size: 1.25rem;
  line-height: 1;
  cursor: pointer;
  color: #6b7280;
  padding: 0 0.25rem;
}
.toast-close:hover { color: #111827; }
</style>
