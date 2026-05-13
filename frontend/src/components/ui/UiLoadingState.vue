<script setup lang="ts">
defineProps<{ message?: string }>();
</script>

<template>
  <div class="ui-loading-state" role="status">
    <div class="ui-spinner" aria-hidden="true" />
    <p v-if="message">{{ message }}</p>
  </div>
</template>

<style scoped>
.ui-loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.75rem;
  padding: 2rem;
  color: #6b7280;
  font-size: 0.875rem;
  font-family: var(--font-base, 'Inter', system-ui, sans-serif);
}

.ui-loading-state p {
  margin: 0;
}

.ui-spinner {
  width: 2rem;
  height: 2rem;
  border: 3px solid #e5e7eb;
  border-top-color: var(--color-primary, #1a56db);
  border-radius: 50%;
  animation: spin 0.75s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}
</style>
