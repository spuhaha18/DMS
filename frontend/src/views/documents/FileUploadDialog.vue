<script setup lang="ts">
import { ref } from 'vue';
import { documentsApi } from '../../api/documents';
import type { DocumentFileSummary } from '../../types';

const props = defineProps<{ docId: number; versionId: number; modelValue: boolean }>();
const emit = defineEmits<{ 'update:modelValue': [val: boolean]; uploaded: [file: DocumentFileSummary] }>();

const fileInput = ref<HTMLInputElement | null>(null);
const selectedFile = ref<File | null>(null);
const progress = ref(0);
const uploading = ref(false);
const error = ref('');
const result = ref<DocumentFileSummary | null>(null);

function selectFile(e: Event) {
  const target = e.target as HTMLInputElement;
  selectedFile.value = target.files?.[0] ?? null;
  error.value = '';
  result.value = null;
}

async function upload() {
  if (!selectedFile.value) return;
  uploading.value = true;
  error.value = '';
  progress.value = 0;
  try {
    result.value = await documentsApi.uploadFile(
      props.docId, props.versionId, selectedFile.value,
      (pct) => { progress.value = pct; }
    );
    emit('uploaded', result.value);
  } catch (e: unknown) {
    const status = (e as { response?: { status?: number; data?: { message?: string } } }).response?.status;
    if (status === 415) {
      error.value = '허용되지 않는 파일 형식입니다. (docx/xlsx/pptx/pdf만 허용)';
    } else if (status === 422) {
      error.value = '파일 내용이 확장자와 일치하지 않습니다.';
    } else {
      error.value = (e as { message?: string }).message ?? '업로드 오류';
    }
  } finally {
    uploading.value = false;
  }
}

function close() {
  emit('update:modelValue', false);
}
</script>

<template>
  <div
    v-if="modelValue"
    style="position: fixed; inset: 0; background: rgba(0,0,0,.5); display: flex; align-items: center; justify-content: center; z-index: 100;"
  >
    <div style="background: white; padding: 24px; border-radius: 8px; min-width: 380px; max-width: 500px;">
      <h2 style="margin-top: 0;">원본 파일 업로드</h2>
      <p style="color: #555; font-size: 13px;">허용 형식: .docx, .xlsx, .pptx, .pdf (최대 100MB)</p>

      <input ref="fileInput" type="file" accept=".docx,.xlsx,.pptx,.pdf" @change="selectFile"
             style="margin-bottom: 12px; display: block;" />

      <div v-if="uploading">
        <div style="background: #eee; border-radius: 4px; height: 8px; margin-bottom: 8px;">
          <div :style="{ width: progress + '%', background: '#0066cc', height: '100%', borderRadius: '4px', transition: 'width .2s' }"></div>
        </div>
        <p style="font-size: 12px; color: #555;">{{ progress }}% 업로드 중...</p>
      </div>

      <div v-if="result" style="background: #f0fff0; padding: 10px; border-radius: 4px; margin-bottom: 8px;">
        <p style="margin: 0; font-size: 13px;">✓ 업로드 완료: <strong>{{ result.fileName }}</strong></p>
        <p style="margin: 4px 0 0; font-size: 11px; color: #555; font-family: monospace;">SHA-256: {{ result.sha256Hash }}</p>
      </div>

      <p v-if="error" style="color: red;">{{ error }}</p>

      <div style="display: flex; gap: 8px; justify-content: flex-end; margin-top: 12px;">
        <button @click="close">{{ result ? '닫기' : '취소' }}</button>
        <button v-if="!result" @click="upload" :disabled="!selectedFile || uploading">
          {{ uploading ? '업로드 중...' : '업로드' }}
        </button>
      </div>
    </div>
  </div>
</template>
