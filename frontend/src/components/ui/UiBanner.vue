<script setup lang="ts">
import { ref } from 'vue';
defineProps<{ type?: 'info' | 'warning' | 'error'; dismissible?: boolean }>();
const visible = ref(true);
</script>

<template>
  <div v-if="visible" :class="['ui-banner', `ui-banner--${type ?? 'info'}`]">
    <span class="ui-banner-content"><slot /></span>
    <button v-if="dismissible" @click="visible = false" class="ui-banner-close" aria-label="닫기">✕</button>
  </div>
</template>

<style scoped>
.ui-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  padding: 0.625rem 1rem;
  font-size: 0.875rem;
  font-family: var(--font-base, 'Inter', system-ui, sans-serif);
}

.ui-banner-content {
  flex: 1;
}

.ui-banner--info {
  background: #eff6ff;
  color: #1e40af;
  border-bottom: 1px solid #bfdbfe;
}

.ui-banner--warning {
  background: #fffbeb;
  color: #92400e;
  border-bottom: 1px solid #fde68a;
}

.ui-banner--error {
  background: #fef2f2;
  color: #991b1b;
  border-bottom: 1px solid #fecaca;
}

.ui-banner-close {
  background: none;
  border: none;
  font-size: 1rem;
  cursor: pointer;
  color: inherit;
  opacity: 0.6;
  padding: 0.125rem 0.375rem;
  border-radius: 0.25rem;
  line-height: 1;
}

.ui-banner-close:hover {
  opacity: 1;
  background: rgba(0, 0, 0, 0.08);
}
</style>
