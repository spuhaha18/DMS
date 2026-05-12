<script setup lang="ts">
import { ref } from 'vue';
import {
  submitVerifyReport,
  fetchManifest,
  extractManifestRenditionSha,
} from '../../api/pdf';

interface Props {
  arrayBuffer: ArrayBuffer | null;
  expectedSha256: string | null;
  contentLength: number | null;
  docId: number;
  versionId: number;
  renditionKind: string;
  stepNumber: number | null;
}

const props = defineProps<Props>();

// ---------------------------------------------------------------------------
// State
// ---------------------------------------------------------------------------

// [I-3] NO_HASH: server did not provide X-File-Sha256 — cannot verify transport
// integrity, but this is NOT a FAIL. Show a distinct warning badge.
type VerifyOutcome = 'PASS' | 'FAIL' | 'NO_HASH' | null;

const loading = ref(false);
const outcome = ref<VerifyOutcome>(null);
const failModalOpen = ref(false);
const failReason = ref<string>('');

// ---------------------------------------------------------------------------
// Hex helper (Web Crypto returns ArrayBuffer; we need lowercase hex)
// ---------------------------------------------------------------------------

function bufToHex(buf: ArrayBuffer): string {
  const bytes = new Uint8Array(buf);
  const out = new Array<string>(bytes.length);
  for (let i = 0; i < bytes.length; i++) {
    out[i] = bytes[i].toString(16).padStart(2, '0');
  }
  return out.join('');
}

// ---------------------------------------------------------------------------
// Core verify flow
// ---------------------------------------------------------------------------

async function verify() {
  if (loading.value) return;
  loading.value = true;
  outcome.value = null;
  failReason.value = '';

  // [I-3] If the server did not send X-File-Sha256, transport hash comparison
  // is impossible. Show a "NO_HASH" warning — do NOT treat as FAIL.
  if (props.expectedSha256 === null) {
    outcome.value = 'NO_HASH';
    loading.value = false;
    return;
  }

  let actualSha: string | null = null;
  let manifestSha: string | null = null;
  let result: 'PASS' | 'FAIL' = 'FAIL';

  try {
    const buf = props.arrayBuffer;
    if (!buf) {
      failReason.value = 'PDF 데이터가 없습니다';
      result = 'FAIL';
    } else if (
      props.contentLength != null &&
      buf.byteLength !== props.contentLength
    ) {
      // (1) Content-Length check — partial body / truncated download.
      failReason.value = `다운로드가 완전하지 않습니다 (수신 ${buf.byteLength} / 기대 ${props.contentLength} 바이트)`;
      result = 'FAIL';
    } else {
      // (2) Transport hash (SHA-256 of the bytes we actually rendered).
      const digest = await crypto.subtle.digest('SHA-256', buf);
      actualSha = bufToHex(digest);

      const transportOk =
        props.expectedSha256 != null &&
        actualSha.toLowerCase() === props.expectedSha256.toLowerCase();

      // (3) Manifest cross-check — graceful on 404 / parse failure.
      let manifestOk: boolean | null = null;
      try {
        const manifest = await fetchManifest(props.docId, props.versionId);
        manifestSha = extractManifestRenditionSha(manifest);
        if (manifestSha) {
          manifestOk = manifestSha.toLowerCase() === actualSha.toLowerCase();
        } else {
          // Manifest endpoint exists but no rendition_sha available → treat as
          // soft-skip: cannot strengthen PASS, but doesn't cause FAIL on its own.
          manifestOk = null;
        }
      } catch {
        // Network/server error on manifest — soft-skip.
        manifestOk = null;
        manifestSha = null;
      }

      if (transportOk && manifestOk !== false) {
        result = 'PASS';
      } else {
        result = 'FAIL';
        if (!transportOk) {
          failReason.value = 'PDF 해시가 서버 기록과 일치하지 않습니다 (전송 무결성 실패).';
        } else if (manifestOk === false) {
          failReason.value = 'PDF 해시가 서명 매니페스트와 일치하지 않습니다.';
        }
      }
    }
  } catch (err) {
    result = 'FAIL';
    failReason.value =
      err instanceof Error ? err.message : '무결성 확인 중 알 수 없는 오류가 발생했습니다';
  }

  outcome.value = result;

  // (4) Always submit verify-report (audit row commits PASS or FAIL).
  try {
    await submitVerifyReport(props.docId, props.versionId, {
      renditionKind: props.renditionKind,
      stepNumber: props.stepNumber,
      verifyResult: result,
      expectedSha256: props.expectedSha256,
      actualSha256: actualSha,
      manifestSha256: manifestSha,
    });
  } catch {
    // The audit submission is best-effort from the UI perspective. The badge
    // already reflects the local verification outcome.
  }

  if (result === 'FAIL') {
    failModalOpen.value = true;
  }

  loading.value = false;
}

function closeModal() {
  failModalOpen.value = false;
}
</script>

<template>
  <span class="verify-wrap">
    <button
      type="button"
      class="verify-btn"
      :disabled="loading || arrayBuffer === null"
      @click="verify"
    >
      {{ loading ? '확인 중...' : '무결성 확인' }}
    </button>

    <span
      v-if="outcome === 'PASS'"
      class="badge badge-pass"
      role="status"
      aria-label="무결성 확인 PASS"
    >
      ✅ PASS
    </span>
    <span
      v-else-if="outcome === 'FAIL'"
      class="badge badge-fail"
      role="status"
      aria-label="무결성 확인 FAIL"
    >
      ❌ FAIL
    </span>
    <span
      v-else-if="outcome === 'NO_HASH'"
      class="badge badge-no-hash"
      role="status"
      aria-label="해시 정보 없음 — 확인 불가"
      title="서버가 X-File-Sha256 헤더를 제공하지 않아 무결성 확인이 불가합니다"
    >
      ⚠️ 해시 정보 없음
    </span>
  </span>

  <!-- FAIL modal (§11.70 — surface the audit + QA contact path) -->
  <div
    v-if="failModalOpen"
    class="modal-overlay"
    role="dialog"
    aria-modal="true"
    aria-labelledby="verify-fail-title"
    @click.self="closeModal"
  >
    <div class="modal-box">
      <h3 id="verify-fail-title" class="modal-title">❌ 무결성 확인 실패</h3>
      <p class="modal-msg">
        {{ failReason || 'PDF 무결성 검사가 실패했습니다.' }}
      </p>
      <p class="modal-msg modal-audit">
        이 결과는 감사 로그에 기록되었습니다.
      </p>
      <p class="modal-msg modal-contact">
        QA 담당자에게 문의하세요.
      </p>
      <div class="modal-actions">
        <button type="button" class="modal-btn-disabled" disabled>
          다운로드 (사용 불가)
        </button>
        <button type="button" class="modal-btn-close" @click="closeModal">
          닫기
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.verify-wrap {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.verify-btn {
  padding: 6px 12px;
  border: 1px solid #2563eb;
  background: #ffffff;
  color: #2563eb;
  border-radius: 4px;
  cursor: pointer;
  font-size: 13px;
}
.verify-btn:hover:not(:disabled) {
  background: #eef2ff;
}
.verify-btn:disabled {
  border-color: #c7c7c7;
  color: #9a9a9a;
  cursor: not-allowed;
}

.badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 600;
}
.badge-pass {
  background: #dcfce7;
  color: #166534;
}
.badge-fail {
  background: #fee2e2;
  color: #991b1b;
}
.badge-no-hash {
  background: #fef3c7;
  color: #92400e;
}

.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}
.modal-box {
  background: #ffffff;
  border-radius: 8px;
  padding: 20px 24px;
  max-width: 480px;
  width: 90%;
  box-shadow: 0 12px 36px rgba(0, 0, 0, 0.2);
}
.modal-title {
  margin: 0 0 12px;
  color: #991b1b;
  font-size: 16px;
}
.modal-msg {
  margin: 6px 0;
  font-size: 14px;
  color: #333333;
}
.modal-audit {
  color: #6b7280;
  font-size: 13px;
}
.modal-contact {
  font-weight: 600;
  color: #111827;
}
.modal-actions {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
.modal-btn-disabled {
  padding: 6px 12px;
  background: #f3f4f6;
  color: #9ca3af;
  border: 1px solid #e5e7eb;
  border-radius: 4px;
  cursor: not-allowed;
}
.modal-btn-close {
  padding: 6px 16px;
  background: #2563eb;
  color: #ffffff;
  border: none;
  border-radius: 4px;
  cursor: pointer;
}
.modal-btn-close:hover {
  background: #1d4ed8;
}
</style>
