import { ref, shallowRef } from 'vue';
import {
  GlobalWorkerOptions,
  getDocument,
  type PDFDocumentProxy,
  type PDFPageProxy,
  type RenderTask,
} from 'pdfjs-dist';
import { api } from '../../api/client';

// -----------------------------------------------------------------------------
// Worker bootstrap (Vite 5 ESM)
// -----------------------------------------------------------------------------
// pdfjs-dist@4.x ships an ESM worker. Vite resolves the URL at build-time via
// `new URL(..., import.meta.url)`. Setting GlobalWorkerOptions.workerSrc once at
// module load is the documented pattern.

const workerUrl = new URL('pdfjs-dist/build/pdf.worker.min.mjs', import.meta.url);
GlobalWorkerOptions.workerSrc = workerUrl.href;

// -----------------------------------------------------------------------------
// State machine
// -----------------------------------------------------------------------------

export const PdfViewerState = {
  Idle: 'idle',
  Loading: 'loading',
  Partial: 'partial',
  Ready: 'ready',
  Error: 'error',
} as const;
export type PdfViewerState = (typeof PdfViewerState)[keyof typeof PdfViewerState];

export interface RenditionHeaders {
  xRenditionKind: string | null;
  xRenditionStep: number | null;
  xFileSha256: string | null;
  contentLength: number | null;
}

/**
 * Centralises pdf.js document loading + per-page rendering + response-header
 * capture for downstream Verify use. Single-instance: each PdfViewer.vue owns one.
 */
export function usePdfViewer() {
  const state = ref<PdfViewerState>(PdfViewerState.Idle);
  const error = ref<string | null>(null);
  const totalPages = ref<number>(0);
  const currentPage = ref<number>(1);

  /**
   * shallowRef intentional: `PDFDocumentProxy` is a large opaque pdf.js handle,
   * making it reactive deeply would force Vue to walk a graph it should not own.
   */
  const pdfDoc = shallowRef<PDFDocumentProxy | null>(null);
  const arrayBuffer = shallowRef<ArrayBuffer | null>(null);
  const headers = ref<RenditionHeaders>({
    xRenditionKind: null,
    xRenditionStep: null,
    xFileSha256: null,
    contentLength: null,
  });

  // [C-1] Sequence counter — incremented on every loadPdf() call so that stale
  // in-flight loads can detect they've been superseded and bail out.
  let loadSeq = 0;

  // [C-2] Active render task — cancelled before starting a new render to prevent
  // concurrent renders writing to the same canvas (canvas tearing).
  let currentRenderTask: RenderTask | null = null;

  function reset() {
    // [C-2] Cancel any in-flight render before resetting state.
    if (currentRenderTask) {
      currentRenderTask.cancel();
      currentRenderTask = null;
    }
    // [C-1] Destroy the previous pdf.js document handle to free worker memory.
    if (pdfDoc.value) {
      void pdfDoc.value.destroy();
    }
    state.value = PdfViewerState.Idle;
    error.value = null;
    totalPages.value = 0;
    currentPage.value = 1;
    pdfDoc.value = null;
    arrayBuffer.value = null;
    headers.value = {
      xRenditionKind: null,
      xRenditionStep: null,
      xFileSha256: null,
      contentLength: null,
    };
  }

  /**
   * Fetches the PDF as an ArrayBuffer (kept in memory for Verify) and hands a
   * COPY to pdf.js. pdf.js detaches/transfers the buffer it consumes, so we
   * must clone first or our Verify check would see a zero-byte buffer.
   *
   * [I-1] Uses a sequence counter to discard results from stale concurrent calls
   * (e.g. rapid src prop changes triggering multiple simultaneous loads).
   */
  async function loadPdf(url: string): Promise<void> {
    const seq = ++loadSeq;
    reset();
    state.value = PdfViewerState.Loading;

    try {
      const res = await api.get<ArrayBuffer>(url, { responseType: 'arraybuffer' });

      // [I-1] Another load() superseded this one — discard result.
      if (seq !== loadSeq) return;

      arrayBuffer.value = res.data;
      const contentLengthHeader = res.headers['content-length'];
      headers.value = {
        xRenditionKind: res.headers['x-rendition-kind'] ?? null,
        xRenditionStep: res.headers['x-rendition-step']
          ? Number(res.headers['x-rendition-step'])
          : null,
        xFileSha256: res.headers['x-file-sha256'] ?? null,
        contentLength: contentLengthHeader ? Number(contentLengthHeader) : res.data.byteLength,
      };

      // pdf.js may detach the ArrayBuffer it owns. Hand it a copy.
      const pdfCopy = res.data.slice(0);
      const loadingTask = getDocument({
        data: pdfCopy,
        // Mandatory security options (§11.70 / DS6 hardening)
        isEvalSupported: false,
        disableAutoFetch: true,
        // Korean (CJK) PDF support — see vite.config.ts viteStaticCopy targets
        cMapUrl: '/cmaps/',
        cMapPacked: true,
        standardFontDataUrl: '/standard_fonts/',
      });

      const doc = await loadingTask.promise;

      // [I-1] Re-check after the async getDocument() call as well.
      if (seq !== loadSeq) {
        void doc.destroy();
        return;
      }

      pdfDoc.value = doc;
      totalPages.value = doc.numPages;
      currentPage.value = 1;
      // First page rendered → Partial; UI moves to Ready after it draws.
      state.value = PdfViewerState.Partial;
    } catch (err: unknown) {
      if (seq !== loadSeq) return; // stale error — ignore
      pdfDoc.value = null;
      state.value = PdfViewerState.Error;
      error.value = err instanceof Error ? err.message : 'PDF 로드에 실패했습니다';
      throw err;
    }
  }

  /**
   * Renders `pageNum` into `canvas` at the requested CSS scale, accounting for
   * device pixel ratio. The viewer component owns the canvas + zoom logic; this
   * helper is pure plumbing.
   */
  async function renderPage(
    pageNum: number,
    canvas: HTMLCanvasElement,
    scale: number,
  ): Promise<void> {
    const doc = pdfDoc.value;
    if (!doc) throw new Error('PDF가 로드되지 않았습니다');
    if (pageNum < 1 || pageNum > doc.numPages) {
      throw new Error(`잘못된 페이지 번호: ${pageNum}`);
    }

    const page: PDFPageProxy = await doc.getPage(pageNum);
    const viewport = page.getViewport({ scale });
    const ctx = canvas.getContext('2d');
    if (!ctx) throw new Error('Canvas 2D context를 가져올 수 없습니다');

    const dpr = window.devicePixelRatio || 1;
    canvas.width = Math.floor(viewport.width * dpr);
    canvas.height = Math.floor(viewport.height * dpr);
    canvas.style.width = `${Math.floor(viewport.width)}px`;
    canvas.style.height = `${Math.floor(viewport.height)}px`;

    const transform = dpr !== 1 ? [dpr, 0, 0, dpr, 0, 0] : undefined;

    // [C-2] Cancel any in-flight render before starting a new one to prevent
    // two renders writing to the same canvas simultaneously (canvas tearing).
    if (currentRenderTask) {
      currentRenderTask.cancel();
      currentRenderTask = null;
    }

    const renderTask = page.render({ canvasContext: ctx, viewport, transform });
    currentRenderTask = renderTask;
    try {
      await renderTask.promise;
    } finally {
      if (currentRenderTask === renderTask) {
        currentRenderTask = null;
      }
    }

    currentPage.value = pageNum;
    if (state.value === PdfViewerState.Partial && pageNum === doc.numPages) {
      state.value = PdfViewerState.Ready;
    } else if (state.value === PdfViewerState.Partial) {
      // Stay in Partial; full Ready triggers when all pages have been seen or
      // — pragmatically — once the first render completes. Most UIs treat that
      // as "ready" since pdf.js streams the rest on demand.
      state.value = PdfViewerState.Ready;
    }
  }

  return {
    state,
    error,
    totalPages,
    currentPage,
    pdfDoc,
    arrayBuffer,
    headers,
    loadPdf,
    renderPage,
    reset,
  };
}
