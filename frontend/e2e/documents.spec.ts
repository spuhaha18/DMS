import { test, expect, Page } from '@playwright/test';

const ADMIN = { userId: 'admin', password: 'TestAdmin1234!' };

async function loginAs(page: Page, userId: string, password: string) {
  await page.goto('/login');
  await page.fill('input[autocomplete="username"]', userId);
  await page.fill('input[type="password"]', password);
  await page.click('button[type="submit"]');
  await expect(page).toHaveURL('/');
}

test.describe('대시보드 (Home)', () => {
  test('관리자 네비게이션 링크가 모두 표시된다', async ({ page }) => {
    await loginAs(page, ADMIN.userId, ADMIN.password);
    await expect(page.locator('a[href="/documents"]')).toBeVisible();
    await expect(page.locator('a[href="/admin/users"]')).toBeVisible();
    await expect(page.locator('a[href="/admin/roles"]')).toBeVisible();
    await expect(page.locator('a[href="/admin/permissions"]')).toBeVisible();
    await expect(page.locator('a[href="/admin/departments"]')).toBeVisible();
    await expect(page.locator('a[href="/admin/categories"]')).toBeVisible();
  });
});

test.describe('문서 목록 (Document List)', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, ADMIN.userId, ADMIN.password);
    await page.goto('/documents');
    await expect(page.locator('h1')).toHaveText('문서 목록');
  });

  test('문서 목록 페이지가 로드된다', async ({ page }) => {
    await expect(page.locator('table, p:has-text("조회 결과가 없습니다")')).toBeVisible();
    await expect(page.locator('button:has-text("+ 문서 등록")')).toBeVisible();
  });

  test('카테고리 필터 셀렉트가 존재한다', async ({ page }) => {
    await expect(page.locator('select').first()).toBeVisible();
  });

  test('"+ 문서 등록" 클릭 → 문서 등록 페이지로 이동', async ({ page }) => {
    await page.click('button:has-text("+ 문서 등록")');
    await expect(page).toHaveURL('/documents/new');
  });
});

async function apiLoginContext(page: Page) {
  await page.goto('/login');
  await page.fill('input[autocomplete="username"]', ADMIN.userId);
  await page.fill('input[type="password"]', ADMIN.password);
  await page.click('button[type="submit"]');
  await expect(page).toHaveURL('/');
}

test.describe('API 직접 검증 (Workflow)', () => {
  test('GET /api/v1/documents → 200', async ({ page }) => {
    await apiLoginContext(page);
    const res = await page.request.get('http://localhost:8080/api/v1/documents');
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body).toHaveProperty('content');
    expect(body).toHaveProperty('totalElements');
  });

  test('GET /api/v1/workflow/my-pending → 200', async ({ page }) => {
    await apiLoginContext(page);
    const res = await page.request.get('http://localhost:8080/api/v1/workflow/my-pending');
    expect(res.status()).toBe(200);
  });

  test('GET /api/v1/auth/me → 로그인된 사용자 반환', async ({ page }) => {
    await apiLoginContext(page);
    const res = await page.request.get('http://localhost:8080/api/v1/auth/me');
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body.userId).toBe(ADMIN.userId);
    expect(body.roles).toContain('ADMIN');
  });

  test('인증 없이 GET /api/v1/documents → 401', async ({ page }) => {
    const res = await page.request.get('http://localhost:8080/api/v1/documents');
    expect(res.status()).toBe(401);
  });
});
