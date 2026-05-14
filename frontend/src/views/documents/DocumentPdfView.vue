<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { documentsApi } from '../../api/documents';
import PdfViewer from '../../components/PdfViewer/PdfViewer.vue';
import ReadAcknowledgeButton from '../../components/ReadAcknowledgeButton.vue';
import { useTrainingStore } from '../../stores/training';
import type { DocumentSummary, DocumentVersionSummary } from '../../types';

// ---------------------------------------------------------------------------
// Route + state
// ---------------------------------------------------------------------------

const route = useRoute();
const router = useRouter();

const docId = computed(() => Number(route.params.docId));
const verId = computed(() => Number(route.params.verId));

// Training assignment integration
const trainingStore = useTrainingStore();
const trainingAssignment = computed(() =>
  trainingStore.assignments.find(a => a.versionId === verId.value) ?? null
);

const kindParam = computed<string | undefined>(() => {
  const k = route.query.kind;
  if (typeof k === 'string' && k.length > 0) return k;
  return undefined;
});
const stepParam = computed<number | null>(() => {
  const s = route.query.step;
  const n = typeof s === 'string' ? Number(s) : Number(Array.isArray(s) ? s[0] : NaN);
  return Number.isFinite(n) ? n : null;
});

const doc = ref<DocumentSummary | null>(null);
const version = ref<DocumentVersionSummary | null>(null);

const loading = ref<boolean>(true);
const errorCode = ref<string | null>(null); // 'NOT_FOUND' | 'NOT_READY' | 'UNKNOWN'
const errorMessage = ref<string>('');
const retryAfterSeconds = ref<number>(30);
const retryCountdown = ref<number>(0);
let retryTimer: number | null = null;

// ---------------------------------------------------------------------------
// Derived
// ---------------------------------------------------------------------------

const renditionKind = computed<string>(() => kindParam.value ?? 'EFFECTIVE');
const stepNumber = computed<number | null>(() => stepParam.value);

/**
 * Build the streaming URL the PdfViewer asks the composable to fetch. We
 * intentionally use a relative path; `api` (axios) prepends `/api/v1`.
 */
const pdfSrc = computed<string>(() => {
  const params = new URLSearchParams();
  if (kindParam.value) params.set('kind', kindParam.value);
  if (stepParam.value != null) params.set('step', String(stepParam.value));
  const qs = params.toString();
  return `/documents/${docId.value}/versions/${verId.value}/pdf${qs ? '?' + qs : ''}`;
});

const availableRenditions = computed(() => [
  { value: 'EFFECTIVE', label: 'EFFECTIVE (최종 + 워터마크)', step: null },
  { value: 'STAMPED', label: 'STAMPED (서명 단계)', step: stepNumber.value },
  { value: 'INITIAL', label: 'INITIAL (변환 직후)', step: null },
]);

// Backend currently does not return canDownload per version. Default true; the
// download button itself is gated by the toolbar UI when the prop is false. A
// follow-up PR can wire `/me`/permissions to compute this client-side.
const userCanDownload = computed<boolean>(() => true);

// ---------------------------------------------------------------------------
// Load
// ---------------------------------------------------------------------------

async function load() {
  loading.value = true;
  errorCode.value = null;
  errorMessage.value = '';
  try {
    const [d, v] = await Promise.all([
      documentsApi.get(docId.value),
      documentsApi.getVersion(docId.value, verId.value),
    ]);
    doc.value = d;
    version.value = v;

    // NOT_READY gate based on pdf_status. The backend also enforces this; we
    // surface a friendly spinner before the viewer attempts a fetch.
    const status = (v.pdfStatus ?? '').toUpperCase();
    if (
      status === 'PENDING_CONVERSION' ||
      status === 'STAMPING' ||
      status === 'WATERMARKING'
    ) {
      errorCode.value = 'NOT_READY';
      retryAfterSeconds.value = 30;
      startRetryCountdown();
    }
  } catch (err: unknown) {
    const status = (err as { response?: { status?: number; data?: { code?: string; retryAfterSeconds?: number } } })
      ?.response?.status;
    const body = (err as { response?: { data?: { code?: string; retryAfterSeconds?: number } } })
      ?.response?.data;

    if (body?.code === 'NOT_READY') {
      errorCode.value = 'NOT_READY';
      retryAfterSeconds.value = body.retryAfterSeconds ?? 30;
      startRetryCountdown();
    } else if (status === 404) {
      errorCode.value = 'NOT_FOUND';
      errorMessage.value = '문서를 찾을 수 없거나 접근 권한이 없습니다';
    } else {
      errorCode.value = 'UNKNOWN';
      errorMessage.value = err instanceof Error ? err.message : '문서 정보를 가져오지 못했습니다';
    }
  } finally {
    loading.value = false;
  }
}

function startRetryCountdown() {
  if (retryTimer != null) window.clearInterval(retryTimer);
  retryCountdown.value = retryAfterSeconds.value;
  retryTimer = window.setInterval(() => {
    retryCountdown.value -= 1;
    if (retryCountdown.value <= 0) {
      if (retryTimer != null) window.clearInterval(retryTimer);
      retryTimer = null;
      void load();
    }
  }, 1000);
}

function manualRetry() {
  if (retryTimer != null) {
    window.clearInterval(retryTimer);
    retryTimer = null;
  }
  void load();
}

function goBack() {
  router.back();
}

function onChangeRendition(payload: { kind: string; step: number | null }) {
  const next: Record<string, string> = { kind: payload.kind };
  if (payload.step != null) next.step = String(payload.step);
  router.replace({
    name: 'document-pdf-view',
    params: { docId: String(docId.value), verId: String(verId.value) },
    query: next,
  });
}

onMounted(() => {
  void load();
  void trainingStore.fetchMyAssignments();
});

// [C-2] Clear any active retry interval when the view is unmounted to prevent
// the timer callback from calling load() on an already-destroyed component.
onUnmounted(() => {
  if (retryTimer != null) {
    window.clearInterval(retryTimer);
    retryTimer = null;
  }
});
</script>

<template>
  <main class="pdf-page">
    <!-- Breadcrumb -->
    <nav class="breadcrumb" aria-label="경로">
      <button class="crumb-link" @click="router.push({ name: 'document-list' })">문서 목록</button>
      <span class="crumb-sep" aria-hidden="true">›</span>
      <button
        class="crumb-link"
        @click="router.push({ name: 'document-detail', params: { docId: String(docId) } })"
      >문서 상세</button>
      <span class="crumb-sep" aria-hidden="true">›</span>
      <span class="crumb-current">PDF 뷰어</span>
    </nav>

    <!-- Document header -->
    <header v-if="doc" class="doc-header">
      <div class="doc-header-row">
        <span class="doc-number">{{ doc.docNumber }}</span>
        <h1 class="doc-title">{{ doc.title }}</h1>
        <span v-if="version" class="status-badge" :class="`status-${(version.pdfStatus ?? '').toLowerCase()}`">
          {{ version.pdfStatus }}
        </span>
        <span v-if="doc.confidential" class="confidential-badge">기밀</span>
      </div>
      <div class="doc-header-meta">
        <span><strong>카테고리</strong> {{ doc.categoryCode }}</span>
        <span><strong>부서</strong> {{ doc.department }}</span>
        <span><strong>렌디션</strong> {{ renditionKind }}<span v-if="stepNumber != null"> · 단계 {{ stepNumber }}</span></span>
      </div>
    </header>

    <!-- States -->
    <section v-if="loading" class="state-block">
      <div class="spinner" aria-hidden="true"></div>
      <p>문서를 불러오는 중...</p>
    </section>

    <section v-else-if="errorCode === 'NOT_READY'" class="state-block">
      <div class="spinner" aria-hidden="true"></div>
      <p>PDF 변환 중입니다.</p>
      <p class="state-sub">
        {{ retryCountdown > 0 ? `${retryCountdown}초 후 자동으로 재시도합니다.` : '재시도 중...' }}
      </p>
      <button type="button" class="primary-btn" @click="manualRetry">지금 재시도</button>
      <button type="button" class="secondary-btn" @click="goBack">뒤로가기</button>
    </section>

    <section v-else-if="errorCode === 'NOT_FOUND'" class="state-block state-error">
      <p><strong>{{ errorMessage }}</strong></p>
      <button type="button" class="secondary-btn" @click="goBack">뒤로가기</button>
    </section>

    <section v-else-if="errorCode === 'UNKNOWN'" class="state-block state-error">
      <p><strong>오류가 발생했습니다.</strong></p>
      <p>{{ errorMessage }}</p>
      <button type="button" class="primary-btn" @click="manualRetry">재시도</button>
      <button type="button" class="secondary-btn" @click="goBack">뒤로가기</button>
    </section>

    <!-- Viewer -->
    <section v-else class="viewer-wrap">
      <PdfViewer
        :src="pdfSrc"
        :rendition-kind="renditionKind"
        :step-number="stepNumber"
        :can-download="userCanDownload"
        :doc-id="docId"
        :version-id="verId"
        :available-renditions="availableRenditions"
        @change-rendition="onChangeRendition"
      />
    </section>

    <!-- Training acknowledgement button (shown when navigated from training list) -->
    <ReadAcknowledgeButton
      v-if="trainingAssignment && !errorCode"
      :assignment-id="trainingAssignment.id"
      :completed="trainingAssignment.completed"
      @acknowledged="trainingStore.fetchMyAssignments()"
    />
  </main>
</template>

<style scoped>
.pdf-page {
  max-width: 1200px;
  margin: 16px auto;
  padding: 0 16px 32px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: calc(100vh - 32px);
}

.breadcrumb {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #6b7280;
}
.crumb-link {
  background: none;
  border: none;
  padding: 0;
  color: #2563eb;
  cursor: pointer;
  font-size: 13px;
}
.crumb-link:hover {
  text-decoration: underline;
}
.crumb-sep {
  color: #9ca3af;
}
.crumb-current {
  color: #111827;
  font-weight: 500;
}

.doc-header {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px 16px;
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
}
.doc-header-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.doc-number {
  font-family: monospace;
  font-size: 13px;
  color: #6b7280;
}
.doc-title {
  font-size: 18px;
  font-weight: 600;
  color: #111827;
  margin: 0;
}
.doc-header-meta {
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: #6b7280;
  flex-wrap: wrap;
}
.doc-header-meta strong {
  color: #374151;
  margin-right: 4px;
  font-weight: 600;
}

.status-badge {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 999px;
  background: #e5e7eb;
  color: #374151;
  font-weight: 600;
}
.status-effective_stamped {
  background: #dcfce7;
  color: #166534;
}
.status-stamped {
  background: #dbeafe;
  color: #1e40af;
}
.status-converted {
  background: #fef3c7;
  color: #92400e;
}
.status-stamping,
.status-watermarking,
.status-pending_conversion {
  background: #fef3c7;
  color: #92400e;
}
.status-conversion_failed,
.status-stamp_failed {
  background: #fee2e2;
  color: #991b1b;
}

.confidential-badge {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 4px;
  background: #fee2e2;
  color: #991b1b;
  font-weight: 600;
}

.state-block {
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  padding: 48px 24px;
  text-align: center;
  color: #4b5563;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}
.state-sub {
  color: #6b7280;
  font-size: 13px;
  margin: 0;
}
.state-error {
  color: #991b1b;
}

.primary-btn,
.secondary-btn {
  padding: 8px 16px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 13px;
  border: 1px solid transparent;
}
.primary-btn {
  background: #2563eb;
  color: #ffffff;
  border-color: #2563eb;
}
.primary-btn:hover {
  background: #1d4ed8;
}
.secondary-btn {
  background: #ffffff;
  color: #374151;
  border-color: #d1d5db;
}
.secondary-btn:hover {
  background: #f3f4f6;
}

.viewer-wrap {
  flex: 1 1 auto;
  min-height: 600px;
  display: flex;
}
.viewer-wrap > * {
  flex: 1 1 auto;
}

.spinner {
  width: 36px;
  height: 36px;
  border: 3px solid #e5e7eb;
  border-top-color: #2563eb;
  border-radius: 50%;
  animation: spin 0.9s linear infinite;
}
@keyframes spin {
  to { transform: rotate(360deg); }
}
</style>
