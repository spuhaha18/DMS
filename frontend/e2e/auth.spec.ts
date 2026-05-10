import { test, expect } from '@playwright/test';

const ADMIN = { userId: 'admin', password: 'TestAdmin1234!' };

test.describe('인증 (Auth)', () => {
  test('로그인 페이지가 렌더링된다', async ({ page }) => {
    await page.goto('/login');
    await expect(page.locator('h1')).toHaveText('EDMS 로그인');
    await expect(page.locator('input[autocomplete="username"]')).toBeVisible();
    await expect(page.locator('input[type="password"]')).toBeVisible();
    await expect(page.locator('button[type="submit"]')).toHaveText('로그인');
  });

  test('잘못된 자격증명 → 오류 메시지', async ({ page }) => {
    await page.goto('/login');
    await page.fill('input[autocomplete="username"]', 'admin');
    await page.fill('input[type="password"]', 'WrongPassword!');
    await page.click('button[type="submit"]');
    await expect(page.locator('p')).toContainText('올바르지 않습니다');
  });

  test('올바른 자격증명 → 대시보드로 리다이렉트', async ({ page }) => {
    await page.goto('/login');
    await page.fill('input[autocomplete="username"]', ADMIN.userId);
    await page.fill('input[type="password"]', ADMIN.password);
    await page.click('button[type="submit"]');
    await expect(page).toHaveURL('/');
    await expect(page.locator('h1')).toHaveText('EDMS 대시보드');
    await expect(page.locator('p')).toContainText('시스템 관리자');
  });

  test('인증 없이 보호된 페이지 접근 → 로그인으로 리다이렉트', async ({ page }) => {
    await page.goto('/documents');
    await expect(page).toHaveURL(/\/login/);
  });

  test('로그아웃 → 로그인 페이지로', async ({ page }) => {
    await page.goto('/login');
    await page.fill('input[autocomplete="username"]', ADMIN.userId);
    await page.fill('input[type="password"]', ADMIN.password);
    await page.click('button[type="submit"]');
    await expect(page).toHaveURL('/');
    await page.click('button:has-text("로그아웃")');
    await expect(page).toHaveURL('/login');
  });
});
