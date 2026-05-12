<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { documentsApi } from '../../api/documents';
import { downloadPdf } from '../../api/pdf';
import FileUploadDialog from './FileUploadDialog.vue';
import type { DocumentSummary, DocumentVersionSummary } from '../../types';

const route = useRoute();
const router = useRouter();

const docId = Number(route.params.docId);
const doc = ref<DocumentSummary | null>(null);
const versions = ref<DocumentVersionSummary[]>([]);
const uploadOpen = ref(false);
const uploadVersionId = ref<number | null>(null);
const downloadingId = ref<number | null>(null);

// PDF statuses that mean the rendition is still being produced — "열람" stays
// hidden for these. Final/usable states (CONVERTED, STAMPED, EFFECTIVE_STAMPED,
// CONVERSION_FAILED, STAMP_FAILED) all surface the link so users can see why.
const PIPELINE_IN_FLIGHT = new Set(['PENDING_CONVERSION', 'STAMPING', 'WATERMARKING']);

onMounted(async () => {
  doc.value = await documentsApi.get(docId);
  versions.value = await documentsApi.listVersions(docId);

  // Auto-open upload dialog if navigated from create
  const uploadParam = route.query.upload as string | undefined;
  if (uploadParam) {
    uploadVersionId.value = Number(uploadParam);
    uploadOpen.value = true;
    // Clear query param without re-navigation
    router.replace({ query: {} });
  }
});

function openUpload(verId: number) {
  uploadVersionId.value = verId;
  uploadOpen.value = true;
}

function onUploaded() {
  uploadOpen.value = false;
  // Refresh versions to show updated source_file_key
  documentsApi.listVersions(docId).then((v) => { versions.value = v; });
}

function canView(v: DocumentVersionSummary): boolean {
  return v.pdfStatus != null && !PIPELINE_IN_FLIGHT.has((v.pdfStatus ?? '').toUpperCase());
}

function canDownloadActive(v: DocumentVersionSummary): boolean {
  // Per the spec, the active "다운로드" button is only enabled when the rendition
  // is EFFECTIVE_STAMPED AND the user has download permission. Backend currently
  // doesn't ship `canDownload` per version → default true; the toolbar enforces
  // the disabled+tooltip pattern when the prop is false.
  return (v.pdfStatus ?? '').toUpperCase() === 'EFFECTIVE_STAMPED' && (v.canDownload ?? true);
}

function shouldShowDisabledDownload(v: DocumentVersionSummary): boolean {
  // Show disabled button when status is EFFECTIVE_STAMPED but user lacks permission.
  return (v.pdfStatus ?? '').toUpperCase() === 'EFFECTIVE_STAMPED' && v.canDownload === false;
}

function gotoPdfView(v: DocumentVersionSummary) {
  router.push({
    name: 'document-pdf-view',
    params: { docId: String(docId), verId: String(v.id) },
  });
}

async function onDownload(v: DocumentVersionSummary) {
  if (!canDownloadActive(v)) return;
  downloadingId.value = v.id;
  try {
    await downloadPdf(docId, v.id);
  } finally {
    downloadingId.value = null;
  }
}

const isConfidential = computed(() => doc.value?.confidential ?? false);
</script>

<template>
  <main style="max-width: 900px; margin: 24px auto; font-family: inherit;">
    <button @click="router.push({ name: 'document-list' })" style="margin-bottom: 16px;">← 목록</button>

    <div v-if="doc">
      <div style="display: flex; gap: 24px; margin-bottom: 20px; flex-wrap: wrap;">
        <div>
          <small style="color: #888;">문서번호</small>
          <p style="font-family: monospace; font-size: 18px; margin: 0;">{{ doc.docNumber }}</p>
        </div>
        <div>
          <small style="color: #888;">제목</small>
          <p style="font-size: 16px; margin: 0;">{{ doc.title }}</p>
        </div>
        <div>
          <small style="color: #888;">카테고리</small>
          <p style="margin: 0;">{{ doc.categoryCode }}</p>
        </div>
        <div>
          <small style="color: #888;">부서</small>
          <p style="margin: 0;">{{ doc.department }}</p>
        </div>
        <div v-if="isConfidential">
          <span style="background: #ffeeee; color: #cc0000; padding: 2px 8px; border-radius: 4px; font-size: 12px;">기밀</span>
        </div>
      </div>

      <h2>버전 목록</h2>
      <table style="width: 100%; border-collapse: collapse;">
        <thead>
          <tr style="background: #f0f0f0;">
            <th style="padding: 6px;">ID</th><th>상태</th><th>PDF 상태</th>
            <th>원본 파일</th><th>등록일</th><th>작업</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="v in versions" :key="v.id" style="border-top: 1px solid #ddd;">
            <td style="padding: 6px;">{{ v.id }}</td>
            <td>{{ v.state }}</td>
            <td>{{ v.pdfStatus }}</td>
            <td>
              <span v-if="v.sourceFileKey" style="font-family: monospace; font-size: 11px; color: #0066cc;">
                {{ v.sourceFileKey.split('/').pop() }}
              </span>
              <span v-else style="color: #aaa;">없음</span>
            </td>
            <td>{{ new Date(v.createdAt).toLocaleDateString('ko-KR') }}</td>
            <td class="version-actions">
              <button
                v-if="v.state === 'DRAFT'"
                @click="openUpload(v.id)"
              >파일 업로드</button>

              <button
                v-if="canView(v)"
                class="action-link"
                @click="gotoPdfView(v)"
              >열람</button>

              <button
                v-if="canDownloadActive(v)"
                class="action-link action-link-primary"
                :disabled="downloadingId === v.id"
                @click="onDownload(v)"
              >
                {{ downloadingId === v.id ? '다운로드 중...' : '다운로드' }}
              </button>
              <button
                v-else-if="shouldShowDisabledDownload(v)"
                class="action-link action-link-disabled"
                disabled
                title="다운로드 권한이 없습니다"
              >다운로드</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- File Upload Dialog -->
    <FileUploadDialog
      v-if="uploadVersionId"
      v-model="uploadOpen"
      :docId="docId"
      :versionId="uploadVersionId"
      @uploaded="onUploaded"
    />
  </main>
</template>

<style scoped>
.version-actions {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  padding: 6px;
}
.action-link {
  font-size: 12px;
  padding: 4px 8px;
  border: 1px solid #d1d5db;
  background: #ffffff;
  border-radius: 4px;
  cursor: pointer;
  color: #2563eb;
}
.action-link:hover:not(:disabled) {
  background: #eef2ff;
}
.action-link-primary {
  background: #2563eb;
  color: #ffffff;
  border-color: #2563eb;
}
.action-link-primary:hover:not(:disabled) {
  background: #1d4ed8;
}
.action-link-disabled {
  background: #f3f4f6;
  color: #9ca3af;
  border-color: #e5e7eb;
  cursor: not-allowed;
}
</style>
