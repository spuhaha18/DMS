<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue';
import { usePdfViewer, PdfViewerState } from './usePdfViewer';
import VerifyButton from './VerifyButton.vue';
import { downloadPdf } from '../../api/pdf';

interface Props {
  /** Backend URL (relative to /api/v1) that returns the PDF as ArrayBuffer. */
  src: string;
  /** Initial rendition kind (EFFECTIVE / STAMPED / INITIAL). */
  renditionKind: string;
  /** Step number when rendition kind is STAMPED. */
  stepNumber?: number | null;
  /** Whether the current user can download (gates the toolbar download btn). */
  canDownload?: boolean;
  /** docId + versionId — required for Verify + download routing. */
  docId: number;
  versionId: number;
  /** Available rendition options for the selector. */
  availableRenditions?: Array<{ value: string; label: string; step?: number | null }>;
}

const props = withDefaults(defineProps<Props>(), {
  stepNumber: null,
  canDownload: false,
  availableRenditions: () => [],
});

const emit = defineEmits<{
  (e: 'change-rendition', value: { kind: string; step: number | null }): void;
}>();

// ---------------------------------------------------------------------------
// Composable
// ---------------------------------------------------------------------------

const {
  state,
  error,
  totalPages,
  currentPage,
  arrayBuffer,
  headers,
  loadPdf,
  renderPage,
  reset,
} = usePdfViewer();

// ---------------------------------------------------------------------------
// UI state
// ---------------------------------------------------------------------------

const canvasRef = ref<HTMLCanvasElement | null>(null);
const containerRef = ref<HTMLDivElement | null>(null);
const scale = ref<number>(1.25);
const fitMode = ref<'manual' | 'page' | 'width'>('manual');
const selectedRendition = ref<string>(props.renditionKind);
const isReady = computed(() =>
  state.value === PdfViewerState.Ready || state.value === PdfViewerState.Partial,
);

// ---------------------------------------------------------------------------
// Load + render lifecycle
// ---------------------------------------------------------------------------

async function load() {
  try {
    await loadPdf(props.src);
    await nextTick();
    await draw();
  } catch {
    // loadPdf already transitioned state → Error and set error.value;
    // swallow here so Vue doesn't log an unhandled mounted-hook error.
  }
}

async function draw() {
  if (!canvasRef.value) return;
  await renderPage(currentPage.value, canvasRef.value, scale.value);
}

onMounted(load);

// [C-1] Release the pdf.js document handle when the component is unmounted
// to prevent memory leaks from worker-side objects.
onUnmounted(() => {
  reset();
});

// Reload when the underlying src changes (rendition switch via query nav).
watch(
  () => props.src,
  async () => {
    await load();
  },
);

// Re-render on page or zoom changes.
watch([currentPage, scale], async () => {
  await draw();
});

// ---------------------------------------------------------------------------
// Toolbar handlers
// ---------------------------------------------------------------------------

function prevPage() {
  if (currentPage.value > 1) currentPage.value -= 1;
}
function nextPage() {
  if (currentPage.value < totalPages.value) currentPage.value += 1;
}
function zoomIn() {
  fitMode.value = 'manual';
  scale.value = Math.min(scale.value + 0.25, 4);
}
function zoomOut() {
  fitMode.value = 'manual';
  scale.value = Math.max(scale.value - 0.25, 0.25);
}
async function fitToPage() {
  fitMode.value = 'page';
  await computeFitScale();
}
async function fitToWidth() {
  fitMode.value = 'width';
  await computeFitScale();
}

/**
 * Computes a scale factor that fits the current page either by full bounding
 * box (fit-page) or by width only (fit-width). Reads the container dimensions
 * and derives the page intrinsic size at scale=1.
 */
async function computeFitScale() {
  if (!canvasRef.value || !containerRef.value) return;
  // Re-render at scale=1 momentarily would be wasteful; instead, read the
  // current canvas CSS dimensions and reverse-engineer the intrinsic size.
  const cssW = canvasRef.value.clientWidth || canvasRef.value.width || 1;
  const cssH = canvasRef.value.clientHeight || canvasRef.value.height || 1;
  const intrinsicW = cssW / scale.value;
  const intrinsicH = cssH / scale.value;
  const availW = containerRef.value.clientWidth - 24; // padding allowance
  const availH = containerRef.value.clientHeight - 24;

  if (fitMode.value === 'width') {
    scale.value = Math.max(0.25, Math.min(4, availW / intrinsicW));
  } else if (fitMode.value === 'page') {
    scale.value = Math.max(
      0.25,
      Math.min(4, Math.min(availW / intrinsicW, availH / intrinsicH)),
    );
  }
}

function onRenditionChange(event: Event) {
  const target = event.target as HTMLSelectElement;
  const value = target.value;
  selectedRendition.value = value;
  // For STAMPED selection we don't know the step from the dropdown alone; parent
  // owns the available list including step values.
  const option = props.availableRenditions.find((o) => o.value === value);
  emit('change-rendition', { kind: value, step: option?.step ?? null });
}

async function onDownloadClick() {
  if (!props.canDownload) return;
  await downloadPdf(props.docId, props.versionId, props.renditionKind, props.stepNumber);
}

// ---------------------------------------------------------------------------
// Verify wiring — headers from composable feed the VerifyButton props
// ---------------------------------------------------------------------------

const verifyKind = computed(() => headers.value.xRenditionKind ?? props.renditionKind);
const verifyStep = computed(() => headers.value.xRenditionStep ?? props.stepNumber);
</script>

<template>
  <div class="pdf-viewer">
    <!-- Toolbar -->
    <div class="pdf-toolbar" role="toolbar" aria-label="PDF 뷰어 도구">
      <div class="pdf-group">
        <button
          type="button"
          class="tb-btn"
          :disabled="!isReady || currentPage <= 1"
          @click="prevPage"
          aria-label="이전 페이지"
        >‹</button>
        <span class="pdf-page-indicator">
          <span aria-live="polite">{{ currentPage }}</span>
          <span aria-hidden="true"> / </span>
          <span>{{ totalPages || '—' }}</span>
        </span>
        <button
          type="button"
          class="tb-btn"
          :disabled="!isReady || currentPage >= totalPages"
          @click="nextPage"
          aria-label="다음 페이지"
        >›</button>
      </div>

      <div class="pdf-group">
        <button
          type="button"
          class="tb-btn"
          :disabled="!isReady"
          @click="zoomOut"
          aria-label="축소"
        >−</button>
        <span class="pdf-zoom-label">{{ Math.round(scale * 100) }}%</span>
        <button
          type="button"
          class="tb-btn"
          :disabled="!isReady"
          @click="zoomIn"
          aria-label="확대"
        >+</button>
        <button
          type="button"
          class="tb-btn"
          :disabled="!isReady"
          @click="fitToPage"
        >페이지 맞춤</button>
        <button
          type="button"
          class="tb-btn"
          :disabled="!isReady"
          @click="fitToWidth"
        >너비 맞춤</button>
      </div>

      <div class="pdf-group" v-if="availableRenditions.length > 0">
        <label class="pdf-select-label">
          렌디션:
          <select
            :value="selectedRendition"
            class="pdf-select"
            @change="onRenditionChange"
          >
            <option
              v-for="opt in availableRenditions"
              :key="opt.value + '_' + (opt.step ?? 'none')"
              :value="opt.value"
            >
              {{ opt.label }}
            </option>
          </select>
        </label>
      </div>

      <div class="pdf-group">
        <button
          v-if="canDownload"
          type="button"
          class="tb-btn tb-btn-primary"
          :disabled="!isReady"
          @click="onDownloadClick"
        >다운로드</button>
        <button
          v-else
          type="button"
          class="tb-btn tb-btn-disabled"
          disabled
          title="다운로드 권한이 없습니다"
        >다운로드</button>

        <VerifyButton
          :array-buffer="arrayBuffer"
          :expected-sha256="headers.xFileSha256"
          :content-length="headers.contentLength"
          :doc-id="docId"
          :version-id="versionId"
          :rendition-kind="verifyKind"
          :step-number="verifyStep"
        />
      </div>
    </div>

    <!-- Canvas surface -->
    <div ref="containerRef" class="pdf-canvas-wrap">
      <div v-if="state === PdfViewerState.Loading" class="pdf-state pdf-state-loading">
        <div class="spinner" aria-hidden="true"></div>
        <span>PDF 로딩 중...</span>
      </div>
      <div v-else-if="state === PdfViewerState.Error" class="pdf-state pdf-state-error">
        <strong>PDF를 표시할 수 없습니다.</strong>
        <p>{{ error ?? '알 수 없는 오류가 발생했습니다' }}</p>
      </div>
      <canvas
        v-show="isReady"
        ref="canvasRef"
        class="pdf-canvas"
        aria-label="PDF 페이지"
      ></canvas>
    </div>
  </div>
</template>

<style scoped>
.pdf-viewer {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #f7f7f8;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  overflow: hidden;
}

.pdf-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  padding: 8px 12px;
  background: #ffffff;
  border-bottom: 1px solid #e5e7eb;
  align-items: center;
}
.pdf-group {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.tb-btn {
  padding: 4px 10px;
  background: #ffffff;
  border: 1px solid #d1d5db;
  border-radius: 4px;
  font-size: 13px;
  color: #111827;
  cursor: pointer;
}
.tb-btn:hover:not(:disabled) {
  background: #f3f4f6;
}
.tb-btn:disabled {
  color: #9ca3af;
  cursor: not-allowed;
  background: #f9fafb;
}
.tb-btn-primary {
  background: #2563eb;
  color: #ffffff;
  border-color: #2563eb;
}
.tb-btn-primary:hover:not(:disabled) {
  background: #1d4ed8;
}
.tb-btn-disabled {
  background: #f3f4f6;
  color: #9ca3af;
  border-color: #e5e7eb;
}

.pdf-page-indicator {
  font-size: 13px;
  min-width: 60px;
  text-align: center;
}
.pdf-zoom-label {
  font-size: 12px;
  color: #6b7280;
  min-width: 44px;
  text-align: center;
}
.pdf-select-label {
  font-size: 13px;
  color: #374151;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.pdf-select {
  font-size: 13px;
  padding: 4px 6px;
  border: 1px solid #d1d5db;
  border-radius: 4px;
}

.pdf-canvas-wrap {
  flex: 1 1 auto;
  overflow: auto;
  padding: 16px;
  display: flex;
  justify-content: center;
  align-items: flex-start;
  min-height: 480px;
}
.pdf-canvas {
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.12);
  background: #ffffff;
}
.pdf-state {
  margin-top: 80px;
  text-align: center;
  color: #6b7280;
}
.pdf-state-error {
  color: #991b1b;
}
.spinner {
  width: 32px;
  height: 32px;
  margin: 0 auto 12px;
  border: 3px solid #e5e7eb;
  border-top-color: #2563eb;
  border-radius: 50%;
  animation: spin 0.9s linear infinite;
}
@keyframes spin {
  to { transform: rotate(360deg); }
}
</style>
