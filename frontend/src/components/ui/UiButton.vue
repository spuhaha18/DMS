<script setup lang="ts">
defineProps<{
  variant?: 'default' | 'primary' | 'danger' | 'ghost';
  disabled?: boolean;
  type?: 'button' | 'submit' | 'reset';
}>();
</script>

<template>
  <button
    :class="['ui-btn', `ui-btn--${variant ?? 'default'}`]"
    :disabled="disabled"
    :type="type ?? 'button'"
  >
    <slot />
  </button>
</template>

<style scoped>
.ui-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.375rem;
  padding: 0.5rem 1rem;
  font-size: 0.875rem;
  font-weight: 500;
  border-radius: 0.375rem;
  border: 1px solid transparent;
  cursor: pointer;
  font-family: var(--font-base, 'Inter', system-ui, sans-serif);
  transition: background 0.15s, border-color 0.15s, color 0.15s;
  line-height: 1.25;
}

.ui-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.ui-btn--default {
  background: #fff;
  border-color: #d1d5db;
  color: #374151;
}

.ui-btn--default:not(:disabled):hover {
  background: #f9fafb;
  border-color: #9ca3af;
}

.ui-btn--primary {
  background: var(--color-primary, #1a56db);
  border-color: var(--color-primary, #1a56db);
  color: #fff;
}

.ui-btn--primary:not(:disabled):hover {
  background: #1648c0;
  border-color: #1648c0;
}

.ui-btn--danger {
  background: #dc2626;
  border-color: #dc2626;
  color: #fff;
}

.ui-btn--danger:not(:disabled):hover {
  background: #b91c1c;
  border-color: #b91c1c;
}

.ui-btn--ghost {
  background: transparent;
  border-color: transparent;
  color: #374151;
}

.ui-btn--ghost:not(:disabled):hover {
  background: #f3f4f6;
}
</style>
