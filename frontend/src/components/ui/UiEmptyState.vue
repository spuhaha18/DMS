<script setup lang="ts">
defineProps<{
  message?: string;
  icon?: string;
}>();
</script>

<template>
  <div class="ui-empty-state">
    <span v-if="icon" class="ui-empty-state-icon" aria-hidden="true">{{ icon }}</span>
    <p class="ui-empty-state-message">{{ message ?? '항목이 없습니다.' }}</p>
    <slot />
  </div>
</template>

<style scoped>
.ui-empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  padding: 3rem 2rem;
  color: #6b7280;
  font-family: var(--font-base, 'Inter', system-ui, sans-serif);
  text-align: center;
}

.ui-empty-state-icon {
  font-size: 2rem;
  line-height: 1;
}

.ui-empty-state-message {
  margin: 0;
  font-size: 0.875rem;
}
</style>
