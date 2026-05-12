import { ref } from 'vue';

export type ToastType = 'success' | 'error' | 'warning' | 'info';

export interface ToastItem {
  id: number;
  message: string;
  type: ToastType;
  duration: number;
}

const toasts = ref<ToastItem[]>([]);
let seq = 0;

const DEFAULT_DURATION = 4000;

function showToast(message: string, type: ToastType = 'info', duration: number = DEFAULT_DURATION) {
  const id = ++seq;
  toasts.value.push({ id, message, type, duration });
}

function dismissToast(id: number) {
  const idx = toasts.value.findIndex((t) => t.id === id);
  if (idx !== -1) toasts.value.splice(idx, 1);
}

// Module-scoped helper so non-Vue code (e.g. axios interceptor) can emit toasts
export function emitToast(message: string, type: ToastType = 'info', duration: number = DEFAULT_DURATION) {
  showToast(message, type, duration);
}

export function useToast() {
  return { toasts, showToast, dismissToast };
}
