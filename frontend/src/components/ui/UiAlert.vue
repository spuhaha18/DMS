<script setup lang="ts">
defineProps<{ type?: 'info' | 'warning' | 'error' | 'success' }>();
</script>

<template>
  <div :class="['ui-alert', `ui-alert--${type ?? 'info'}`]" role="alert">
    <slot />
  </div>
</template>

<style scoped>
.ui-alert {
  padding: 0.75rem 1rem;
  border-radius: 0.375rem;
  border-left-width: 4px;
  border-left-style: solid;
  font-size: 0.875rem;
  line-height: 1.5;
}

.ui-alert--info {
  background: #eff6ff;
  border-left-color: var(--color-primary, #1a56db);
  color: #1e40af;
}

.ui-alert--warning {
  background: #fffbeb;
  border-left-color: #d97706;
  color: #92400e;
}

.ui-alert--error {
  background: #fef2f2;
  border-left-color: #dc2626;
  color: #991b1b;
}

.ui-alert--success {
  background: #f0fdf4;
  border-left-color: #16a34a;
  color: #166534;
}
</style>
