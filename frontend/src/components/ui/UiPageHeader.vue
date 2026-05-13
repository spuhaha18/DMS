<script setup lang="ts">
defineProps<{
  title: string;
  subtitle?: string;
}>();
</script>

<template>
  <div class="ui-page-header">
    <div class="ui-page-header-text">
      <h1>{{ title }}</h1>
      <p v-if="subtitle">{{ subtitle }}</p>
    </div>
    <div v-if="$slots.actions" class="ui-page-header-actions">
      <slot name="actions" />
    </div>
  </div>
</template>

<style scoped>
.ui-page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 1.5rem;
}

.ui-page-header-text h1 {
  margin: 0;
  font-size: 1.5rem;
  font-weight: 700;
  color: #111827;
  font-family: var(--font-base, 'Inter', system-ui, sans-serif);
}

.ui-page-header-text p {
  margin: 0.25rem 0 0;
  font-size: 0.875rem;
  color: #6b7280;
}

.ui-page-header-actions {
  display: flex;
  gap: 0.5rem;
  align-items: center;
  flex-shrink: 0;
}
</style>
