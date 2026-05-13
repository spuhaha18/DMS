<script setup lang="ts">
defineProps<{
  variant?: 'default' | 'info' | 'success' | 'warning' | 'danger';
  title?: string;
}>();
</script>

<template>
  <span :class="['ui-badge', `ui-badge--${variant ?? 'default'}`]" :title="title">
    <slot />
  </span>
</template>

<style scoped>
.ui-badge {
  display: inline-flex;
  align-items: center;
  padding: 0.125rem 0.5rem;
  font-size: 0.75rem;
  font-weight: 500;
  border-radius: 9999px;
  line-height: 1.4;
  font-family: var(--font-base, 'Inter', system-ui, sans-serif);
}

.ui-badge--default {
  background: #f3f4f6;
  color: #374151;
}

.ui-badge--info {
  background: #eff6ff;
  color: #1d4ed8;
}

.ui-badge--success {
  background: #f0fdf4;
  color: #15803d;
}

.ui-badge--warning {
  background: #fffbeb;
  color: #b45309;
}

.ui-badge--danger {
  background: #fef2f2;
  color: #b91c1c;
}
</style>
