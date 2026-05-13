<script setup lang="ts">
defineProps<{ type?: 'error' | 'info' }>();
</script>

<template>
  <p :class="['ui-hint', `ui-hint--${type ?? 'info'}`]"><slot /></p>
</template>

<style scoped>
.ui-hint {
  margin: 0.25rem 0 0;
  font-size: 0.75rem;
  line-height: 1.4;
}

.ui-hint--info {
  color: #6b7280;
}

.ui-hint--error {
  color: #dc2626;
}
</style>
