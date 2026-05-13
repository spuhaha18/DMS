<script setup lang="ts">
import { ref, computed } from 'vue';
import UiModal from '../ui/UiModal.vue';
import UiAlert from '../ui/UiAlert.vue';
import UiHint from '../ui/UiHint.vue';
import UiButton from '../ui/UiButton.vue';
import UiField from '../ui/UiField.vue';
import type { ResearchProject } from '../../types';

const props = defineProps<{ project: ResearchProject; modelValue: boolean }>();
const emit = defineEmits<{
  'update:modelValue': [boolean];
  'confirmed': [{ terminationDate: string }];
}>();

const terminationDate = ref('');
const typedCode = ref('');
const busy = ref(false);
const today = new Date().toISOString().slice(0, 10);
const codeMatch = computed(() => typedCode.value === props.project.projectCode);
const canConfirm = computed(() => codeMatch.value && !!terminationDate.value && !busy.value);

function close() {
  if (busy.value) return;
  emit('update:modelValue', false);
}

async function confirm() {
  if (!canConfirm.value) return;
  busy.value = true;
  emit('confirmed', { terminationDate: terminationDate.value });
}
</script>

<template>
  <UiModal
    title="중단/종료 전이 — 보존기간 자동 연장 (단축 불가)"
    :model-value="modelValue"
    @update:model-value="close"
  >
    <UiAlert type="warning">
      ⚠️ 이 작업 이후 <strong>{{ project.projectCode }}</strong>에 연결된 모든 문서의 보존기간은
      <strong>{{ terminationDate || '(중단/종료일 입력 필요)' }}부터 {{ project.retentionYears ?? '?' }}년</strong>으로
      자동 연장됩니다. 21 CFR Part 11 규제상 <strong>한번 연장된 보존기간은 줄일 수 없습니다.</strong>
    </UiAlert>

    <UiField label="중단/종료일" :required="true">
      <input type="date" v-model="terminationDate" :max="today" required />
    </UiField>

    <UiField label="확인을 위해 과제코드를 정확히 입력하세요">
      <input v-model="typedCode" :placeholder="project.projectCode" />
      <UiHint v-if="typedCode && !codeMatch" type="error">코드가 일치하지 않습니다</UiHint>
    </UiField>

    <template #footer>
      <UiButton @click="close" :disabled="busy">취소</UiButton>
      <UiButton variant="danger" :disabled="!canConfirm" @click="confirm">
        {{ busy ? '처리 중…' : '중단/종료 확정' }}
      </UiButton>
    </template>
  </UiModal>
</template>

<style scoped>
input {
  padding: 0.5rem 0.75rem;
  border: 1px solid #d1d5db;
  border-radius: 0.375rem;
  font-size: 0.875rem;
  font-family: inherit;
  color: #111827;
}

input:focus {
  outline: 2px solid var(--color-primary, #1a56db);
  outline-offset: -1px;
  border-color: transparent;
}
</style>
