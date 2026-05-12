/**
 * Seed helpers for PDF viewer E2E tests
 *
 * 전체 스택(Docker Compose + backend + Gotenberg + MinIO + frontend)이
 * 실행 중인 환경에서만 동작한다.
 *
 * 기동 방법:
 *   docker compose up -d
 *   cd frontend && npx playwright test pdf-viewer.spec.ts
 */

const BACKEND_URL = process.env.BACKEND_URL ?? 'http://localhost:8080';

// ---------------------------------------------------------------------------
// 테스트 계정 목록 (OQ §3.1 계정과 일치)
// ---------------------------------------------------------------------------
export const TEST_USERS = {
  admin:      { userId: 'admin',             password: 'TestAdmin1234!' },
  author:     { userId: 'oq-author-01',      password: 'TestUser1234!' },
  reviewer:   { userId: 'oq-reviewer-01',    password: 'TestUser1234!' },
  general:    { userId: 'oq-viewer-01',      password: 'TestUser1234!' },
  auditor:    { userId: 'oq-auditor-01',     password: 'TestUser1234!' },
  noDownload: { userId: 'oq-nodownload-01',  password: 'TestUser1234!' },
} as const;

// ---------------------------------------------------------------------------
// API 헬퍼: 로그인 → 세션 쿠키 반환
// ---------------------------------------------------------------------------
/**
 * 주어진 자격증명으로 백엔드 API에 로그인하고 Set-Cookie 헤더에서
 * 세션 쿠키 문자열을 추출하여 반환한다.
 *
 * TODO: 라이브 스택 연결 후 실제 구현 필요.
 *       현재는 stub — 실제 호출 시 에러가 발생한다.
 *
 * @param userId   사용자 ID
 * @param password 비밀번호
 * @returns        `JSESSIONID=...` 형태의 쿠키 문자열
 */
export async function apiLogin(userId: string, password: string): Promise<string> {
  // TODO: 라이브 스택에서 구현
  const response = await fetch(`${BACKEND_URL}/api/v1/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ userId, password }),
    credentials: 'include',
  });

  if (!response.ok) {
    throw new Error(`apiLogin failed: ${response.status} ${await response.text()}`);
  }

  const cookie = response.headers.get('set-cookie');
  if (!cookie) {
    throw new Error('apiLogin: no Set-Cookie header in response');
  }
  return cookie;
}

// ---------------------------------------------------------------------------
// 테스트 문서 타입
// ---------------------------------------------------------------------------
export interface TestDocument {
  docId: number;
  verId: number;
  pdfStatus: string;
}

// ---------------------------------------------------------------------------
// API 헬퍼: 테스트 문서 생성 → 문서 ID·버전 ID 반환
// ---------------------------------------------------------------------------
/**
 * admin 세션으로 SOP 테스트 문서를 생성하고 {docId, verId, pdfStatus} 를 반환한다.
 *
 * TODO: 라이브 스택 연결 후 실제 구현 필요.
 *       생성할 문서 상태(DRAFT/EFFECTIVE 등)는 호출측이 adminCookie 를 통해 제어한다.
 *
 * @param adminCookie  admin 세션 쿠키 (apiLogin 으로 획득)
 * @returns            생성된 문서의 식별자 및 pdf 상태
 */
export async function createTestDocument(adminCookie: string): Promise<TestDocument> {
  // TODO: 라이브 스택에서 구현
  // 1. POST /api/v1/documents — 문서 생성
  // 2. POST /api/v1/documents/{docId}/versions/{verId}/files — 파일 업로드
  // 3. GET  /api/v1/documents/{docId}/versions/{verId} — pdfStatus 폴링
  throw new Error(
    'createTestDocument: not implemented — requires live Docker Compose stack',
  );
}
