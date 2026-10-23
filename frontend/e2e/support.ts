import { expect, type Browser, type Page } from '@playwright/test'

/** Test users from infra/keycloak; every password is "password". */
export type TestUser = 'student' | 'lecturer' | 'staff.academic' | 'staff.finance' | 'admin'

/** Signs in through the real Keycloak page, in a browser context of its own. */
export async function signIn(browser: Browser, user: TestUser): Promise<Page> {
  const context = await browser.newContext()
  const page = await context.newPage()
  await page.goto('/')
  await page.getByLabel(/username/i).fill(user)
  await page.getByLabel('Password', { exact: true }).fill('password')
  await page.getByRole('button', { name: /sign in/i }).click()
  await expect(page.getByRole('button', { name: 'Sign out' })).toBeVisible()
  return page
}

export function pdf(name: string, text: string) {
  return { name, mimeType: 'application/pdf', buffer: Buffer.from(`%PDF-1.7\n% ${text}\n`) }
}

/** A confirmation toast, e.g. "Step completed". */
export function toast(page: Page, text: string) {
  return page.getByRole('status').filter({ hasText: text })
}

/** The request code shown under a request's title, e.g. ACAD-0042. */
export async function requestCode(page: Page): Promise<string> {
  const code = await page.locator('header .mono').first().textContent()
  return (code ?? '').trim()
}
