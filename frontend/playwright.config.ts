import { defineConfig, devices } from '@playwright/test'

/**
 * End-to-end tests against a running stack: docker compose infrastructure, all services and the gateway.
 * They sign in through Keycloak like a person would. Not part of CI, which has no stack to run them on.
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  fullyParallel: false,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:5173',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: process.env.E2E_BASE_URL
    ? undefined
    : { command: 'pnpm dev', url: 'http://localhost:5173', reuseExistingServer: true },
})
