import { defineConfig, devices } from '@playwright/test';

import { API_BASE, E2E_BACKEND_PORT, E2E_WEB_PORT, WEB_BASE_URL } from './e2e/fixtures/e2e-env';

/**
 * Configuration Playwright des tests E2E de PRIMATIS.
 *
 * Les scénarios sont exécutés séquentiellement afin de préserver une exécution
 * déterministe. Les serveurs backend et frontend utilisent des ports dédiés et
 * ne réutilisent jamais une instance déjà active.
 */
export default defineConfig({
  testDir: './e2e/tests',
  globalSetup: './e2e/global-setup.ts',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['html', { outputFolder: 'e2e/report', open: 'never' }], ['list']],

  use: {
    baseURL: WEB_BASE_URL,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },

  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],

  webServer: [
    {
      // Backend Spring Boot utilisant le profil E2E et un port dédié.
      command: `bash -c 'set -a && source .env.local && set +a && SPRING_PROFILES_ACTIVE=e2e SERVER_PORT=${E2E_BACKEND_PORT} PRIMATIS_CORS_ALLOWED_ORIGINS=${WEB_BASE_URL} ./mvnw spring-boot:run'`,
      cwd: '../primatis-api',
      url: `${API_BASE}/titles`,
      reuseExistingServer: false,
      timeout: 180_000,
    },
    {
      // Frontend Angular utilisant le proxy dédié aux tests E2E.
      command: `npx ng serve --port ${E2E_WEB_PORT} --proxy-config proxy.conf.e2e.json`,
      cwd: '.',
      url: `${WEB_BASE_URL}/login`,
      reuseExistingServer: false,
      timeout: 120_000,
    },
  ],
});
