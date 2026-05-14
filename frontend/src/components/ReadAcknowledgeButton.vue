<!-- frontend/src/components/ReadAcknowledgeButton.vue -->
<script setup lang="ts">
import { ref, computed } from 'vue';
import { useTrainingStore } from '../stores/training';

const props = defineProps<{
  assignmentId: number | null;
  completed: boolean;
}>();

const emit = defineEmits<{
  acknowledged: [];
}>();

const store = useTrainingStore();
const isLoading = ref(false);
const error = ref<string | null>(null);

const visible = computed(() => props.assignmentId !== null && !props.completed);

async function handleClick() {
  if (props.assignmentId === null) return;
  isLoading.value = true;
  error.value = null;
  try {
    await store.acknowledge(props.assignmentId);
    emit('acknowledged');
  } catch (e) {
    error.value = e instanceof Error ? e.message : '확인 처리 실패';
  } finally {
    isLoading.value = false;
  }
}
</script>

<template>
  <div v-if="visible" class="read-acknowledge">
    <button
      :disabled="isLoading"
      aria-label="읽고 이해하였음 확인"
      class="acknowledge-btn"
      @click="handleClick"
    >
      {{ isLoading ? '처리 중...' : '읽고 이해하였음' }}
    </button>
    <p v-if="error" role="alert" class="error">{{ error }}</p>
  </div>
  <div v-else-if="completed" class="acknowledged-badge">
    ✓ 열람 확인 완료
  </div>
</template>

<style scoped>
.read-acknowledge {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 16px;
}

.acknowledge-btn {
  padding: 10px 24px;
  background: #2563eb;
  color: #ffffff;
  border: none;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
}

.acknowledge-btn:hover:not(:disabled) {
  background: #1d4ed8;
}

.acknowledge-btn:disabled {
  background: #93c5fd;
  cursor: not-allowed;
}

.error {
  color: #991b1b;
  font-size: 13px;
  margin: 0;
}

.acknowledged-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 8px 16px;
  background: #dcfce7;
  color: #166534;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 600;
}
</style>
