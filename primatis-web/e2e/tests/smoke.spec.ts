import { expect, test } from '@playwright/test';

import { e2eAccounts } from '../fixtures/e2e-accounts';


test('un compte MEMBER de la baseline E2E peut se connecter de bout en bout', async ({ page }) => {
  await page.goto('/login');
  await expect(page.getByRole('heading', { level: 1, name: 'Accéder à votre espace PRIMATIS' })).toBeVisible();

  await page.locator('#login-email').fill(e2eAccounts.member.email);
  await page.locator('#login-password').fill(e2eAccounts.member.password);
  await page.getByRole('button', { name: 'Se connecter' }).click();

  // Navigation réelle hors de /login : preuve que le backend a répondu
  // avec succès (un échec laisserait l'utilisateur sur /login avec un
  // p-message d'erreur, jamais de redirection).
  await expect(page).not.toHaveURL(/\/login/);

  // État authentifié reflété par l'UI (navigation réelle, pas un état
  // local simulé) : le bouton de déconnexion n'apparaît que si
  // AuthService.authenticated() lit un JWT réel et non expiré.
  await expect(page.locator('.nav-logout')).toBeVisible();

  // Preuve directe qu'un JWT a été émis par le backend et persisté par le
  // frontend, exactement comme le décrit AuthService (sessionStorage,
  // jamais localStorage).
  const storedToken = await page.evaluate(() => sessionStorage.getItem('primatis.accessToken'));
  expect(storedToken).toBeTruthy();
});
