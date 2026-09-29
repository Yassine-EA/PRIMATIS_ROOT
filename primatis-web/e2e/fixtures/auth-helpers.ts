import { Page } from '@playwright/test';

import { e2eAccounts } from './e2e-accounts';

type E2eAccount = (typeof e2eAccounts)[keyof typeof e2eAccounts];

const SESSION_STORAGE_KEY = 'primatis.accessToken';


export async function loginAs(page: Page, account: E2eAccount): Promise<void> {
  await page.goto('/login');
  await page.locator('#login-email').fill(account.email);
  await page.locator('#login-password').fill(account.password);
  await page.getByRole('button', { name: 'Se connecter' }).click();
  await page.waitForURL((url) => !url.pathname.startsWith('/login'));
}


export function getStoredToken(page: Page): Promise<string | null> {
  return page.evaluate((key) => sessionStorage.getItem(key), SESSION_STORAGE_KEY);
}

/** Déclenche le logout réel via la navigation (bouton ".nav-logout", jamais un appel direct à AuthService depuis le test). */
export async function logoutViaUi(page: Page): Promise<void> {
  await page.locator('.nav-logout').click();
}
