import type { AxiosResponse } from 'axios';
import { api } from './client';

export interface VerifyReportPayload {
  renditionKind: string;
  stepNumber: number | null;
  verifyResult: 'PASS' | 'FAIL' | 'NO_HASH';
  expectedSha256: string | null;
  actualSha256: string | null;
  manifestSha256: string | null;
}

/**
 * Frontend signature of the manifest cross-check response. The backend currently
 * exposes only `GET /signatures` (a list of summary/detail DTOs), not a dedicated
 * `/manifest` endpoint, so we accept a flexible shape and graceful 404.
 *
 * Expected fields when present:
 *   - `canonical_payload.v3.rendition_sha256` (preferred)
 *   - `rendition_sha256` (flat fallback)
 */
export interface ManifestResponse {
  canonical_payload?: {
    v3?: {
      rendition_sha256?: string;
    };
  };
  rendition_sha256?: string;
  [extra: string]: unknown;
}

function buildPdfPath(
  docId: number,
  versionId: number,
  suffix: string = '',
): string {
  return `/documents/${docId}/versions/${versionId}/pdf${suffix}`;
}

function buildPdfParams(kind?: string, step?: number | null): Record<string, string | number> {
  const params: Record<string, string | number> = {};
  if (kind) params.kind = kind;
  if (step != null) params.step = step;
  return params;
}

/**
 * Streams a PDF rendition as ArrayBuffer. The caller keeps the ArrayBuffer in
 * memory for §11.70 Verify (no re-download).
 *
 * Response headers exposed by the backend (PR1):
 *   - X-Rendition-Kind, X-Rendition-Step, X-File-Sha256, Content-Length
 */
export async function fetchPdfStream(
  docId: number,
  versionId: number,
  kind?: string,
  step?: number | null,
): Promise<AxiosResponse<ArrayBuffer>> {
  return api.get<ArrayBuffer>(buildPdfPath(docId, versionId), {
    params: buildPdfParams(kind, step),
    responseType: 'arraybuffer',
  });
}

/**
 * Triggers a browser download via Content-Disposition: attachment. Uses a
 * hidden anchor to surface the OS save dialog without leaving the SPA.
 */
export async function downloadPdf(
  docId: number,
  versionId: number,
  kind?: string,
  step?: number | null,
): Promise<void> {
  const res = await api.get<ArrayBuffer>(buildPdfPath(docId, versionId, '/download'), {
    params: buildPdfParams(kind, step),
    responseType: 'arraybuffer',
  });

  // Best-effort filename from Content-Disposition; fall back to a generic name.
  const cd: string | undefined = res.headers['content-disposition'];
  let filename = 'document.pdf';
  if (cd) {
    const match = /filename\s*=\s*"?([^";]+)"?/i.exec(cd);
    if (match && match[1]) filename = match[1].trim();
  }

  const blob = new Blob([res.data], { type: 'application/pdf' });
  const url = URL.createObjectURL(blob);
  try {
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    a.remove();
  } finally {
    // Defer revocation so the browser has time to start the download.
    setTimeout(() => URL.revokeObjectURL(url), 5000);
  }
}

/**
 * Records the §11.70 integrity verification result. Backend persists a
 * PDF_VERIFIED audit row regardless of PASS/FAIL.
 */
export async function submitVerifyReport(
  docId: number,
  versionId: number,
  payload: VerifyReportPayload,
): Promise<void> {
  await api.post(buildPdfPath(docId, versionId, '/verify-report'), payload);
}

/**
 * Fetches the signature manifest for the manifest cross-check (§11.70).
 *
 * Returns `null` when the endpoint is absent (404) so the verifier can
 * fall back to the transport-hash check alone without failing the whole flow.
 */
export async function fetchManifest(
  docId: number,
  versionId: number,
): Promise<ManifestResponse | null> {
  try {
    const res = await api.get<ManifestResponse>(
      `/documents/${docId}/versions/${versionId}/signatures/manifest`,
    );
    return res.data;
  } catch (err: unknown) {
    const status = (err as { response?: { status?: number } })?.response?.status;
    if (status === 404) return null;
    throw err;
  }
}

/**
 * Extracts the rendition SHA-256 from a manifest response.
 *
 * Tries (in order):
 *   1. Structured shape: `canonical_payload.v3.rendition_sha256`
 *   2. Flat shape: `rendition_sha256`
 *   3. Pipe-delimited v3 string (DS §8.1): last `|`-separated field
 *      `signer_id|meaning|signed_at|version_id|doc_number|revision|doc_status|original_sha|rendition_sha`
 */
export function extractManifestRenditionSha(manifest: ManifestResponse | null): string | null {
  if (!manifest) return null;
  const structured = manifest.canonical_payload?.v3?.rendition_sha256;
  if (structured) return structured;
  if (typeof manifest.rendition_sha256 === 'string') return manifest.rendition_sha256;

  // Some responses ship the raw canonical_payload as a string. Parse it if so.
  // (Index-signature access widens to `unknown`, so we narrow before split.)
  const raw: unknown = (manifest as Record<string, unknown>)['canonical_payload'];
  if (typeof raw === 'string') {
    const parts = raw.split('|');
    // v3 has 9 fields; v2 has 8. We want the last field only when it's v3.
    if (parts.length >= 9) return parts[parts.length - 1] || null;
  }
  return null;
}
