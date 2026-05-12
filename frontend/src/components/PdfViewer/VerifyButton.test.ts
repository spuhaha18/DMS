import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { nextTick } from 'vue';
import VerifyButton from './VerifyButton.vue';

vi.mock('../../api/pdf', () => ({
  submitVerifyReport: vi.fn(),
  fetchManifest: vi.fn(),
  extractManifestRenditionSha: vi.fn(),
}));

import { submitVerifyReport, fetchManifest, extractManifestRenditionSha } from '../../api/pdf';

// Pre-computed SHA-256 of bytes [0x01, 0x02, 0x03, 0x04] (lowercase hex).
// We feed the exact same bytes into the component so the digest matches.
const KNOWN_BYTES = new Uint8Array([1, 2, 3, 4]).buffer;
const KNOWN_SHA = '9f64a747e1b97f131fabb6b447296c9b6f0201e79fb3c5356e6c77e89b6a806a';

/**
 * The verify flow chains: digest → fetchManifest → submitVerifyReport, with
 * Vue reactive updates in-between. happy-dom doesn't drain microtasks
 * deterministically with a single flushPromises(), so we loop flushPromises +
 * a real-timer setTimeout(0) to yield to the macrotask queue (where the digest
 * callback resolves), then a final nextTick() so Vue can flush DOM updates.
 */
async function runAllAsync(): Promise<void> {
  for (let i = 0; i < 5; i++) {
    await flushPromises();
    await new Promise((r) => setTimeout(r, 0));
  }
  await flushPromises();
  await nextTick();
}

function makeProps(overrides: Record<string, unknown> = {}) {
  return {
    arrayBuffer: KNOWN_BYTES,
    expectedSha256: KNOWN_SHA,
    contentLength: KNOWN_BYTES.byteLength,
    docId: 1,
    versionId: 2,
    renditionKind: 'EFFECTIVE',
    stepNumber: null,
    ...overrides,
  };
}

describe('VerifyButton', () => {
  // Track wrappers so we can drop them between tests — happy-dom does not
  // reset document state between iterations, so leftover modal overlays from
  // previous mounts can leak into subsequent `wrapper.find()` lookups.
  type AnyWrapper = ReturnType<typeof mount<typeof VerifyButton>>;
  const wrappers: AnyWrapper[] = [];
  function trackMount(w: AnyWrapper): AnyWrapper {
    wrappers.push(w);
    return w;
  }
  afterEach(() => {
    while (wrappers.length) {
      const w = wrappers.pop();
      try { w?.unmount(); } catch { /* swallow */ }
    }
    document.body.innerHTML = '';
  });

  beforeEach(() => {
    // mockReset() clears the implementation + return value queue; clearAllMocks
    // would only wipe call history, leading to cross-test mock-value bleed.
    // Use mockImplementation uniformly (mixing mockReturnValue + mockResolvedValue
    // produced confusing precedence behaviour across consecutive tests).
    (submitVerifyReport as any).mockReset().mockImplementation(async () => undefined);
    (fetchManifest as any).mockReset().mockImplementation(async () => null);
    (extractManifestRenditionSha as any).mockReset().mockImplementation(() => null);
  });

  it('renders the verify button and is disabled when arrayBuffer is null', () => {
    const wrapper = trackMount(mount(VerifyButton, {
      props: makeProps({ arrayBuffer: null }),
    }));
    const btn = wrapper.find('button.verify-btn');
    expect(btn.text()).toContain('무결성 확인');
    expect(btn.attributes('disabled')).toBeDefined();
  });

  it('PASS when expected sha matches digest and no manifest contradicts', async () => {
    const wrapper = trackMount(mount(VerifyButton, { props: makeProps() }));
    await wrapper.find('button.verify-btn').trigger('click');
    await runAllAsync();

    expect(wrapper.find('.badge-pass').exists()).toBe(true);
    expect(submitVerifyReport).toHaveBeenCalledWith(
      1,
      2,
      expect.objectContaining({
        verifyResult: 'PASS',
        actualSha256: KNOWN_SHA,
        expectedSha256: KNOWN_SHA,
      }),
    );
  });

  it('FAIL when content-length mismatches (truncated body)', async () => {
    const wrapper = trackMount(mount(VerifyButton, {
      props: makeProps({ contentLength: 9999 }),
    }));
    await wrapper.find('button.verify-btn').trigger('click');
    await runAllAsync();

    expect(wrapper.find('.badge-fail').exists()).toBe(true);
    expect(wrapper.find('.modal-overlay').exists()).toBe(true);
    expect(submitVerifyReport).toHaveBeenCalledWith(
      1, 2,
      expect.objectContaining({ verifyResult: 'FAIL' }),
    );
  });

  it('FAIL when transport hash does not match expected', async () => {
    const wrapper = trackMount(mount(VerifyButton, {
      props: makeProps({ expectedSha256: 'ffffffff' }),
    }));
    await wrapper.find('button.verify-btn').trigger('click');
    await runAllAsync();

    expect(wrapper.find('.badge-fail').exists()).toBe(true);
    expect(wrapper.find('.modal-overlay').exists()).toBe(true);
  });

  it('FAIL when manifest contradicts the transport hash', async () => {
    (fetchManifest as any).mockImplementation(async () => ({ rendition_sha256: 'differentvalue' }));
    (extractManifestRenditionSha as any).mockImplementation(() => 'differentvalue');

    const wrapper = trackMount(mount(VerifyButton, { props: makeProps() }));
    await wrapper.find('button.verify-btn').trigger('click');
    await runAllAsync();

    expect(wrapper.find('.badge-fail').exists()).toBe(true);
  });

  it('PASS when manifest fetch fails gracefully (404 → null)', async () => {
    // beforeEach already wires the "manifest absent" path; no per-test override.
    const wrapper = trackMount(mount(VerifyButton, { props: makeProps() }));
    await wrapper.find('button.verify-btn').trigger('click');
    await runAllAsync();

    // Transport hash matches and manifest is soft-skipped → PASS
    expect(wrapper.find('.badge-pass').exists()).toBe(true);
  });

  it('Modal close button hides the overlay (FAIL state)', async () => {
    const wrapper = trackMount(mount(VerifyButton, {
      props: makeProps({ contentLength: 0 }),
    }));
    await wrapper.find('button.verify-btn').trigger('click');
    await runAllAsync();

    expect(wrapper.find('.modal-overlay').exists()).toBe(true);
    await wrapper.find('.modal-btn-close').trigger('click');
    expect(wrapper.find('.modal-overlay').exists()).toBe(false);
  });
});
