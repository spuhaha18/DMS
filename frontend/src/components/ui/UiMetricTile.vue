<script setup lang="ts">
defineProps<{
  label: string;
  value: string | number;
  unit?: string;
  trend?: 'up' | 'down' | 'neutral';
}>();
</script>

<template>
  <div class="ui-metric-tile">
    <span class="ui-metric-tile-label">{{ label }}</span>
    <div class="ui-metric-tile-value">
      <strong>{{ value }}</strong>
      <span v-if="unit" class="ui-metric-tile-unit">{{ unit }}</span>
    </div>
    <slot />
  </div>
</template>

<style scoped>
.ui-metric-tile {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  padding: 1rem 1.25rem;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 0.5rem;
  font-family: var(--font-base, 'Inter', system-ui, sans-serif);
}

.ui-metric-tile-label {
  font-size: 0.75rem;
  font-weight: 500;
  color: #6b7280;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.ui-metric-tile-value {
  display: flex;
  align-items: baseline;
  gap: 0.25rem;
}

.ui-metric-tile-value strong {
  font-size: 1.5rem;
  font-weight: 700;
  color: #111827;
  line-height: 1.2;
}

.ui-metric-tile-unit {
  font-size: 0.875rem;
  color: #6b7280;
}
</style>
