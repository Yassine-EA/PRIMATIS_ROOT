import { request } from '@playwright/test';

import { API_BASE } from './fixtures/e2e-env';
import { e2eAccounts } from './fixtures/e2e-accounts';


export default async function globalSetup(): Promise<void> {
  const context = await request.newContext();
  try {
    const login = await context.post(`${API_BASE}/auth/login`, {
      data: { email: e2eAccounts.admin.email, password: e2eAccounts.admin.password },
    });
    if (login.status() !== 200) {
      throw new Error(
        `Le backend joint sur ${API_BASE} n'est pas un backend E2E (login du compte E2E refusé : HTTP ${login.status()}).`,
      );
    }
    const { token } = await login.json();
    const marker = await context.get(`${API_BASE}/e2e/time`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    if (marker.status() !== 200) {
      throw new Error(
        `Le backend joint sur ${API_BASE} n'expose pas /e2e/time (HTTP ${marker.status()}) : profil "e2e" absent.`,
      );
    }
  } catch (error) {
    const reason = error instanceof Error ? error.message : String(error);
    throw new Error(`REFUS E2E (garde-fou F-07) — aucun test n'a été exécuté. ${reason}`);
  } finally {
    await context.dispose();
  }
}
