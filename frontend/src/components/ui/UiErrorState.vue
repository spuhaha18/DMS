<script setup lang="ts">
defineProps<{ error?: string; retry?: () => void }>();
</script>

<template>
  <div class="ui-error-state" role="alert">
    <p class="ui-error-state-message">{{ error ?? '오류가 발생했습니다.' }}</p>
    <button v-if="retry" @click="retry" class="ui-error-state-retry">다시 시도</button>
  </div>
</template>

<style scoped>
.ui-error-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.75rem;
  padding: 2rem;
  font-family: var(--font-base, 'Inter', system-ui, sans-serif);
}

.ui-error-state-message {
  margin: 0;
  font-size: 0.875rem;
  color: #dc2626;
  text-align: center;
}

.ui-error-state-retry {
  padding: 0.375rem 0.875rem;
  font-size: 0.875rem;
  background: #fff;
  border: 1px solid #d1d5db;
  border-radius: 0.375rem;
  cursor: pointer;
  color: #374151;
  font-family: inherit;
}

.ui-error-state-retry:hover {
  background: #f9fafb;
  border-color: #9ca3af;
}
</style>
