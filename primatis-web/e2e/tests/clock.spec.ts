import { expect, test } from '@playwright/test';

import { e2eAccounts } from '../fixtures/e2e-accounts';
import { advanceE2eTime, getE2eTime, resetE2eTime } from '../fixtures/e2e-time-helpers';
import { API_BASE } from '../fixtures/e2e-env';


test.afterEach(async ({ request }) => {
  await resetE2eTime(request);
});

test('le Clock E2E peut être avancé puis réinitialisé au temps réel', async ({ request }) => {
  const before = await getE2eTime(request);
  expect(before.offsetSeconds).toBe(0);
  expect(Math.abs(new Date(before.currentTime).getTime() - Date.now())).toBeLessThan(60_000);

  const advanced = await advanceE2eTime(request, 48);
  expect(advanced.offsetSeconds).toBe(48 * 3600);
  const deltaMs = new Date(advanced.currentTime).getTime() - new Date(before.currentTime).getTime();
  expect(deltaMs).toBeGreaterThanOrEqual(48 * 3600 * 1000 - 5_000);
  expect(deltaMs).toBeLessThanOrEqual(48 * 3600 * 1000 + 60_000);

  const afterReset = await resetE2eTime(request);
  expect(afterReset.offsetSeconds).toBe(0);
  expect(Math.abs(new Date(afterReset.currentTime).getTime() - Date.now())).toBeLessThan(60_000);
});

test('la surface e2e/time est protégée (ADMIN autorisé, MEMBER refusé, sans token refusé)', async ({ request }) => {
  const noToken = await request.get(`${API_BASE}/e2e/time`);
  expect(noToken.status()).toBe(401);

  const memberLogin = await request.post(`${API_BASE}/auth/login`, {
    data: { email: e2eAccounts.member.email, password: e2eAccounts.member.password },
  });
  const { token: memberToken } = await memberLogin.json();
  const memberAttempt = await request.get(`${API_BASE}/e2e/time`, {
    headers: { Authorization: `Bearer ${memberToken}` },
  });
  expect(memberAttempt.status()).toBe(403);

  const adminState = await getE2eTime(request);
  expect(adminState.offsetSeconds).toBe(0);
});
