<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { documentsApi } from '../../api/documents';
import FileUploadDialog from './FileUploadDialog.vue';
import type { DocumentSummary, DocumentVersionSummary } from '../../types';

const route = useRoute();
const router = useRouter();

const docId = Number(route.params.docId);
const doc = ref<DocumentSummary | null>(null);
const versions = ref<DocumentVersionSummary[]>([]);
const uploadOpen = ref(false);
const uploadVersionId = ref<number | null>(null);

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
        <div v-if="doc.confidential">
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
            <td>
              <button v-if="v.state === 'DRAFT'" @click="openUpload(v.id)">파일 업로드</button>
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
