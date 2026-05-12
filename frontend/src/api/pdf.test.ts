import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  extractManifestRenditionSha,
  fetchManifest,
  fetchPdfStream,
  submitVerifyReport,
  type ManifestResponse,
} from './pdf';
import { api } from './client';

vi.mock('./client', () => ({
  api: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

describe('pdf api', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });
  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe('extractManifestRenditionSha', () => {
    it('returns null for null input', () => {
      expect(extractManifestRenditionSha(null)).toBeNull();
    });

    it('reads structured canonical_payload.v3.rendition_sha256', () => {
      const m: ManifestResponse = {
        canonical_payload: { v3: { rendition_sha256: 'aa11' } },
      };
      expect(extractManifestRenditionSha(m)).toBe('aa11');
    });

    it('falls back to flat rendition_sha256', () => {
      const m: ManifestResponse = { rendition_sha256: 'bb22' };
      expect(extractManifestRenditionSha(m)).toBe('bb22');
    });

    it('parses the v3 pipe-delimited canonical_payload string', () => {
      // 9 fields = v3 (last is rendition_sha)
      const raw = '1|APPROVE|2026-01-01T00:00:00Z|42|DOC-001|3|EFFECTIVE|origSha|RENDSHA';
      const m = { canonical_payload: raw } as unknown as ManifestResponse;
      expect(extractManifestRenditionSha(m)).toBe('RENDSHA');
    });

    it('does NOT extract from v2 pipe-delimited (only 8 fields)', () => {
      const raw = '1|APPROVE|2026-01-01T00:00:00Z|42|DOC-001|3|EFFECTIVE|origSha';
      const m = { canonical_payload: raw } as unknown as ManifestResponse;
      expect(extractManifestRenditionSha(m)).toBeNull();
    });

    it('returns null when no rendition is present in any shape', () => {
      expect(extractManifestRenditionSha({})).toBeNull();
    });
  });

  describe('fetchManifest', () => {
    it('returns data on success', async () => {
      (api.get as any).mockResolvedValue({ data: { rendition_sha256: 'x' } });
      const m = await fetchManifest(1, 2);
      expect(m).toEqual({ rendition_sha256: 'x' });
      expect(api.get).toHaveBeenCalledWith('/documents/1/versions/2/signatures/manifest');
    });

    it('returns null on 404 (graceful skip)', async () => {
      const err = { response: { status: 404 } };
      (api.get as any).mockRejectedValue(err);
      const m = await fetchManifest(1, 2);
      expect(m).toBeNull();
    });

    it('rethrows non-404 errors', async () => {
      (api.get as any).mockRejectedValue({ response: { status: 500 } });
      await expect(fetchManifest(1, 2)).rejects.toBeDefined();
    });
  });

  describe('fetchPdfStream', () => {
    it('requests arraybuffer with params', async () => {
      (api.get as any).mockResolvedValue({
        data: new ArrayBuffer(8),
        headers: {},
      });
      await fetchPdfStream(10, 20, 'STAMPED', 2);
      expect(api.get).toHaveBeenCalledWith('/documents/10/versions/20/pdf', {
        params: { kind: 'STAMPED', step: 2 },
        responseType: 'arraybuffer',
      });
    });

    it('omits empty params', async () => {
      (api.get as any).mockResolvedValue({ data: new ArrayBuffer(0), headers: {} });
      await fetchPdfStream(10, 20);
      expect(api.get).toHaveBeenCalledWith('/documents/10/versions/20/pdf', {
        params: {},
        responseType: 'arraybuffer',
      });
    });
  });

  describe('submitVerifyReport', () => {
    it('posts the report payload', async () => {
      (api.post as any).mockResolvedValue({});
      await submitVerifyReport(5, 6, {
        renditionKind: 'EFFECTIVE',
        stepNumber: null,
        verifyResult: 'PASS',
        expectedSha256: 'aa',
        actualSha256: 'aa',
        manifestSha256: 'aa',
      });
      expect(api.post).toHaveBeenCalledWith(
        '/documents/5/versions/6/pdf/verify-report',
        expect.objectContaining({ verifyResult: 'PASS' }),
      );
    });
  });
});
