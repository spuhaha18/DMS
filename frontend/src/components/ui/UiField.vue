<script setup lang="ts">
defineProps<{
  label?: string;
  required?: boolean;
  hint?: string;
  error?: string;
}>();
</script>

<template>
  <div class="ui-field">
    <label v-if="label" class="ui-field-label">
      {{ label }}
      <span v-if="required" aria-required="true" class="ui-field-required">*</span>
    </label>
    <slot />
    <p v-if="error" class="ui-field-error" role="alert">{{ error }}</p>
    <p v-else-if="hint" class="ui-field-hint">{{ hint }}</p>
  </div>
</template>

<style scoped>
.ui-field {
  display: flex;
  flex-direction: column;
  gap: 0.375rem;
}

.ui-field-label {
  font-size: 0.875rem;
  font-weight: 500;
  color: #374151;
  font-family: var(--font-base, 'Inter', system-ui, sans-serif);
}

.ui-field-required {
  color: #dc2626;
  margin-left: 0.125rem;
}

.ui-field-error {
  margin: 0;
  font-size: 0.75rem;
  color: #dc2626;
  font-family: var(--font-base, 'Inter', system-ui, sans-serif);
}

.ui-field-hint {
  margin: 0;
  font-size: 0.75rem;
  color: #6b7280;
  font-family: var(--font-base, 'Inter', system-ui, sans-serif);
}
</style>
