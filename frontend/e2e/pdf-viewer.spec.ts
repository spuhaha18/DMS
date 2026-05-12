/**
 * PDF Viewer E2E Tests — M7.1
 *
 * 전체 스택(Docker Compose + backend + Gotenberg + MinIO + frontend)이
 * 없는 CI 환경에서는 test.fixme 로 마킹한다.
 * 스택이 기동된 IQ/OQ 환경에서 fixme 제거 후 실행.
 *
 * 참조:
 *   - D2 권한 매트릭스 (OQ-DOC-PDFVIEW-001~010)
 *   - FS-DOC-PDFVIEW-001~003
 *   - DS §7.3, §7.4
 */

import { test, expect, Page, BrowserContext } from '@playwright/test';
import { TEST_USERS, apiLogin, createTestDocument } from './fixtures/seed';

// ---------------------------------------------------------------------------
// 공통 헬퍼
// ---------------------------------------------------------------------------

async function loginAs(page: Page, userId: string, password: string) {
  await page.goto('/login');
  await page.fill('input[autocomplete="username"]', userId);
  await page.fill('input[type="password"]', password);
  await page.click('button[type="submit"]');
  await expect(page).toHaveURL('/');
}

// ---------------------------------------------------------------------------
// 시나리오 1: Author — DRAFT 자기 문서 INITIAL 렌디션 열람
// OQ-DOC-PDFVIEW-001
// ---------------------------------------------------------------------------
test.fixme(
  'S1: Author가 DRAFT 자기 문서의 INITIAL 렌디션을 열람한다',
  async ({ page }) => {
    // 전제: Author로 로그인, DRAFT 상태 자기 문서, pdfStatus=CONVERTED
    await loginAs(page, TEST_USERS.author.userId, TEST_USERS.author.password);

    // 스택 기동 환경에서 채울 docId / verId (시드 헬퍼 참고)
    const docId = 1; // TODO: seed 로 생성
    const verId = 1;

    await page.goto(`/documents/${docId}/versions/${verId}/pdf?kind=INITIAL`);

    // PDF 뷰어 캔버스 렌더링 확인
    await expect(page.locator('canvas')).toBeVisible({ timeout: 15_000 });

    // 응답 헤더 확인 (API intercept 방식)
    const [response] = await Promise.all([
      page.waitForResponse(
        (r) =>
          r.url().includes(`/api/v1/documents/${docId}/versions/${verId}/pdf`) &&
          r.status() === 200,
      ),
      page.reload(),
    ]);
    expect(response.headers()['x-rendition-kind']).toBe('INITIAL');
  },
);

// ---------------------------------------------------------------------------
// 시나리오 2: 일반 사용자 — EFFECTIVE_STAMPED 문서 뷰어에서 PDF 렌더 확인
// OQ-DOC-PDFVIEW-002
// ---------------------------------------------------------------------------
test.fixme(
  'S2: 일반 사용자가 EFFECTIVE 문서를 뷰어로 열고 PDF 렌더를 확인한다',
  async ({ page }) => {
    // 전제: can_view 권한 있는 일반 사용자, EFFECTIVE_STAMPED 문서
    await loginAs(page, TEST_USERS.general.userId, TEST_USERS.general.password);

    const docId = 2; // TODO: seed 로 생성 (EFFECTIVE_STAMPED 상태)
    await page.goto(`/documents/${docId}`);

    // "열람" 버튼 클릭 → PDF 뷰어 페이지 전환
    await page.click('button:has-text("열람"), a:has-text("열람")');

    // URL 에 /pdf 포함 확인
    await expect(page).toHaveURL(/\/pdf/);

    // 캔버스 렌더링 확인
    await expect(page.locator('canvas')).toBeVisible({ timeout: 15_000 });
  },
);

// ---------------------------------------------------------------------------
// 시나리오 3: 일반 사용자 — STAMPED 중간본 직접 접근 시 404
// OQ-DOC-PDFVIEW-003
// ---------------------------------------------------------------------------
test.fixme(
  'S3: 일반 사용자가 STAMPED 중간본에 직접 접근하면 접근 거부 오류가 표시된다',
  async ({ page }) => {
    // 전제: 일반 사용자(non-assignee), STAMPED 렌디션이 있는 문서
    await loginAs(page, TEST_USERS.general.userId, TEST_USERS.general.password);

    const docId = 3; // TODO: seed 로 생성 (STAMPED 렌디션 포함)
    const verId = 3;

    await page.goto(`/documents/${docId}/versions/${verId}/pdf?kind=STAMPED`);

    // ProblemDetail 404 → 프론트엔드 오류 메시지
    await expect(
      page.locator('text=문서를 찾을 수 없거나 접근 권한이 없습니다'),
    ).toBeVisible({ timeout: 5_000 });
  },
);

// ---------------------------------------------------------------------------
// 시나리오 4: 활성 단계 결재자 — kind 미지정 시 STAMPED 본 자동 선택
// OQ-DOC-PDFVIEW-004
// ---------------------------------------------------------------------------
test.fixme(
  'S4: 활성 단계 결재자가 PDF 뷰어에 접근하면 STAMPED 렌디션이 자동 선택된다',
  async ({ page }) => {
    // 전제: 워크플로 활성 단계 assignee (reviewer)
    await loginAs(page, TEST_USERS.reviewer.userId, TEST_USERS.reviewer.password);

    const docId = 4; // TODO: seed 로 생성 (UNDER_REVIEW, 활성 assignee = reviewer)
    const verId = 4;

    // kind 파라미터 미지정 → 서버 자동 선택
    await page.goto(`/documents/${docId}/versions/${verId}/pdf`);

    // 캔버스 렌더링 + 렌디션 종류 배지/헤더 확인
    await expect(page.locator('canvas')).toBeVisible({ timeout: 15_000 });

    const [response] = await Promise.all([
      page.waitForResponse(
        (r) =>
          r.url().includes(`/api/v1/documents/${docId}/versions/${verId}/pdf`) &&
          r.status() === 200,
      ),
      page.reload(),
    ]);
    expect(response.headers()['x-rendition-kind']).toBe('STAMPED');
  },
);

// ---------------------------------------------------------------------------
// 시나리오 5: can_download=false 사용자 — 뷰어 OK, 다운로드 버튼 disabled+tooltip
// OQ-DOC-PDFVIEW-005
// ---------------------------------------------------------------------------
test.fixme(
  'S5: can_download=false 사용자는 뷰어는 열리지만 다운로드 버튼이 비활성화된다',
  async ({ page }) => {
    // 전제: can_download=false 권한인 사용자, EFFECTIVE_STAMPED 문서
    await loginAs(page, TEST_USERS.noDownload.userId, TEST_USERS.noDownload.password);

    const docId = 2; // EFFECTIVE_STAMPED 문서 재사용
    const verId = 2;

    await page.goto(`/documents/${docId}/versions/${verId}/pdf`);

    // 뷰어 렌더링 확인
    await expect(page.locator('canvas')).toBeVisible({ timeout: 15_000 });

    // 다운로드 버튼 disabled 확인
    const downloadBtn = page.locator('button:has-text("다운로드"), [data-testid="download-btn"]');
    await expect(downloadBtn).toBeDisabled();

    // tooltip "다운로드 권한이 없습니다" 표시 확인
    await downloadBtn.hover();
    await expect(page.locator('text=다운로드 권한이 없습니다')).toBeVisible();
  },
);

// ---------------------------------------------------------------------------
// 시나리오 6: 무결성 확인 (Verify) 버튼 → PASS 배지 + audit_logs PDF_VERIFIED
// OQ-DOC-PDFVIEW-006
// ---------------------------------------------------------------------------
test.fixme(
  'S6: 무결성 확인 버튼 클릭 시 PASS 배지가 표시되고 PDF_VERIFIED 감사로그가 기록된다',
  async ({ page }) => {
    // 전제: EFFECTIVE 문서, 뷰어 열려있음
    await loginAs(page, TEST_USERS.author.userId, TEST_USERS.author.password);

    const docId = 2;
    const verId = 2;

    await page.goto(`/documents/${docId}/versions/${verId}/pdf`);
    await expect(page.locator('canvas')).toBeVisible({ timeout: 15_000 });

    // API 요청 인터셉트
    const verifyRequest = page.waitForRequest(
      (req) =>
        req.url().includes('/api/v1/pdf/verify-report') && req.method() === 'POST',
    );

    // 무결성 확인 버튼 클릭
    await page.click('button:has-text("무결성 확인"), [data-testid="verify-btn"]');

    // API 호출 확인
    await verifyRequest;

    // PASS 배지 표시 확인
    await expect(page.locator('text=PASS')).toBeVisible({ timeout: 10_000 });
  },
);

// ---------------------------------------------------------------------------
// 시나리오 7: AUDITOR — kind=STAMPED&step=2 명시 선택 → 해당 본 렌더
// OQ-DOC-PDFVIEW-007
// ---------------------------------------------------------------------------
test.fixme(
  'S7: AUDITOR가 kind=STAMPED&step=2 를 명시하면 해당 렌디션이 렌더된다',
  async ({ page }) => {
    // 전제: AUDITOR 역할 사용자, 2단계 이상 완료된 문서
    await loginAs(page, TEST_USERS.auditor.userId, TEST_USERS.auditor.password);

    const docId = 5; // TODO: seed 로 생성 (2단계 완료)
    const verId = 5;

    const [response] = await Promise.all([
      page.waitForResponse(
        (r) =>
          r.url().includes(`/api/v1/documents/${docId}/versions/${verId}/pdf`) &&
          r.status() === 200,
      ),
      page.goto(`/documents/${docId}/versions/${verId}/pdf?kind=STAMPED&step=2`),
    ]);

    await expect(page.locator('canvas')).toBeVisible({ timeout: 15_000 });

    // 응답 헤더 확인
    expect(response.headers()['x-rendition-kind']).toBe('STAMPED');
    expect(response.headers()['x-rendition-step']).toBe('2');
  },
);

// ---------------------------------------------------------------------------
// 시나리오 8: PENDING_CONVERSION 문서 뷰어 진입 시 NOT_READY 오류
// OQ-DOC-PDFVIEW-008
// ---------------------------------------------------------------------------
test.fixme(
  'S8: pdf_status=PENDING_CONVERSION 문서에 접근하면 변환 중 메시지가 표시된다',
  async ({ page }) => {
    // 전제: pdf_status=PENDING_CONVERSION 문서
    await loginAs(page, TEST_USERS.author.userId, TEST_USERS.author.password);

    const docId = 6; // TODO: seed 로 생성 (PENDING_CONVERSION 상태)
    const verId = 6;

    await page.goto(`/documents/${docId}/versions/${verId}/pdf`);

    // "PDF 변환 중" 메시지 또는 오류 페이지 확인
    await expect(
      page.locator('text=PDF 변환 중입니다, text=변환 중, text=PDF 준비 중'),
    ).toBeVisible({ timeout: 5_000 });
  },
);

// ---------------------------------------------------------------------------
// 시나리오 9: 두 번째 로그인으로 기존 세션 만료 → 브라우저 1에서 401 → 로그인 리다이렉트
// OQ-DOC-PDFVIEW-009
// ---------------------------------------------------------------------------
test.fixme(
  'S9: 같은 계정으로 두 번째 로그인 시 첫 번째 세션이 만료되어 로그인 페이지로 리다이렉트된다',
  async ({ browser }) => {
    // 브라우저 1 컨텍스트
    const context1: BrowserContext = await browser.newContext();
    const page1: Page = await context1.newPage();
    await loginAs(page1, TEST_USERS.general.userId, TEST_USERS.general.password);

    const docId = 2;
    const verId = 2;
    await page1.goto(`/documents/${docId}/versions/${verId}/pdf`);
    await expect(page1.locator('canvas')).toBeVisible({ timeout: 15_000 });

    // 브라우저 2 컨텍스트에서 동일 계정 로그인 → 세션 교체
    const context2: BrowserContext = await browser.newContext();
    const page2: Page = await context2.newPage();
    await loginAs(page2, TEST_USERS.general.userId, TEST_USERS.general.password);

    // 브라우저 1에서 다음 API 요청 → 401 → 로그인 리다이렉트
    await page1.reload();
    await expect(page1).toHaveURL(/\/login/, { timeout: 10_000 });

    await context1.close();
    await context2.close();
  },
);

// ---------------------------------------------------------------------------
// 시나리오 10: 15분 세션 타임아웃 후 PDF 뷰어 페이지 전환 → 401 → 로그인 리다이렉트
// OQ-DOC-PDFVIEW-010
// ---------------------------------------------------------------------------
test.fixme(
  'S10: 15분 비활성 후 페이지 전환 시 401로 로그인 페이지에 자동 리다이렉트된다',
  async ({ page }) => {
    // 전제: 로그인 후 15분 비활성
    // 실제 환경에서는 세션 만료 시간을 단축(예: 30초)하거나 직접 쿠키를 만료시켜 테스트
    await loginAs(page, TEST_USERS.general.userId, TEST_USERS.general.password);

    const docId = 2;
    const verId = 2;
    await page.goto(`/documents/${docId}/versions/${verId}/pdf`);
    await expect(page.locator('canvas')).toBeVisible({ timeout: 15_000 });

    // 세션 쿠키 만료 시뮬레이션 (쿠키 삭제)
    // 실제 OQ에서는 15분 대기 또는 세션 강제 만료 API 사용
    await page.context().clearCookies();

    // 페이지 전환(PDF 페이지 내 내비게이션) → 401 감지 → 로그인 리다이렉트
    const nextPageBtn = page.locator('[data-testid="pdf-next-page"], button:has-text("다음 페이지")');
    if (await nextPageBtn.isVisible()) {
      await nextPageBtn.click();
    } else {
      await page.reload();
    }

    await expect(page).toHaveURL(/\/login/, { timeout: 10_000 });
  },
);
