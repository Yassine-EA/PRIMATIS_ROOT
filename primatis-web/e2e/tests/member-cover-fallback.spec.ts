import { expect, test } from '@playwright/test';


test('le fallback compact de couverture des pages MEMBER est servi', async ({ request }) => {
  const response = await request.get('/assets/fallbacks/cover-compact.svg');

  expect(response.status()).toBe(200);
  expect(response.headers()['content-type']).toContain('image/svg+xml');
});
