<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue';

defineProps<{ title: string; modelValue: boolean }>();
const emit = defineEmits<{ 'update:modelValue': [boolean] }>();

function close() { emit('update:modelValue', false); }

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape') close();
}

onMounted(() => window.addEventListener('keydown', onKeydown));
onUnmounted(() => window.removeEventListener('keydown', onKeydown));
</script>

<template>
  <Teleport to="body">
    <div v-if="modelValue" class="ui-modal-backdrop" @click.self="close">
      <div class="ui-modal" role="dialog" :aria-label="title">
        <div class="ui-modal-header">
          <h3>{{ title }}</h3>
          <button class="ui-modal-close" @click="close" aria-label="닫기">✕</button>
        </div>
        <div class="ui-modal-body"><slot /></div>
        <div v-if="$slots.footer" class="ui-modal-footer"><slot name="footer" /></div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.ui-modal-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.ui-modal {
  background: #fff;
  border-radius: 0.5rem;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.18);
  min-width: 400px;
  max-width: 600px;
  width: 100%;
  display: flex;
  flex-direction: column;
  font-family: var(--font-base, 'Inter', system-ui, sans-serif);
}

.ui-modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 1rem 1.25rem;
  border-bottom: 1px solid #e5e7eb;
}

.ui-modal-header h3 {
  margin: 0;
  font-size: 1rem;
  font-weight: 600;
  color: #111827;
}

.ui-modal-close {
  background: none;
  border: none;
  font-size: 1.1rem;
  cursor: pointer;
  color: #6b7280;
  padding: 0.25rem 0.5rem;
  border-radius: 0.25rem;
  line-height: 1;
}

.ui-modal-close:hover {
  color: #111827;
  background: #f3f4f6;
}

.ui-modal-body {
  padding: 1.25rem;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.ui-modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 0.5rem;
  padding: 0.75rem 1.25rem;
  border-top: 1px solid #e5e7eb;
  background: #f9fafb;
  border-radius: 0 0 0.5rem 0.5rem;
}
</style>
