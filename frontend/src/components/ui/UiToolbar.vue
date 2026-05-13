<script setup lang="ts">
defineProps<{
  sticky?: boolean;
}>();
</script>

<template>
  <div :class="['ui-toolbar', { 'ui-toolbar--sticky': sticky }]">
    <div v-if="$slots.start" class="ui-toolbar-start">
      <slot name="start" />
    </div>
    <div v-if="$slots.default" class="ui-toolbar-center">
      <slot />
    </div>
    <div v-if="$slots.end" class="ui-toolbar-end">
      <slot name="end" />
    </div>
  </div>
</template>

<style scoped>
.ui-toolbar {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.625rem 0;
  font-family: var(--font-base, 'Inter', system-ui, sans-serif);
}

.ui-toolbar--sticky {
  position: sticky;
  top: 0;
  z-index: 10;
  background: #fff;
  border-bottom: 1px solid #e5e7eb;
  padding: 0.625rem 1rem;
}

.ui-toolbar-start {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.ui-toolbar-center {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.ui-toolbar-end {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-left: auto;
}
</style>
