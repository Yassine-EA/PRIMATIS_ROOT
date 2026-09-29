import { Page, expect } from '@playwright/test';



export async function createLoanViaUi(
  page: Page,
  params: { memberNumber: string; titleName: string; copyInventoryCode: string },
): Promise<void> {
  await page.goto('/staff/loans');
  await page.getByRole('button', { name: 'Enregistrer un prêt' }).click();

  await page.locator('#loan-create-borrower-search').fill(params.memberNumber);
  const borrowerResult = page.locator('li', { hasText: params.memberNumber });
  await expect(borrowerResult).toBeVisible();
  await borrowerResult.getByRole('button', { name: 'Sélectionner' }).click();

  await page.locator('#loan-create-title-search').fill(params.titleName);
  const titleResult = page.locator('li', { hasText: params.titleName });
  await expect(titleResult).toBeVisible();
  await titleResult.getByRole('button', { name: 'Sélectionner' }).click();

  const copyResult = page.locator('li', { hasText: params.copyInventoryCode });
  await expect(copyResult).toBeVisible();
  // Preuve visuelle que le Copy est AVAILABLE avant l'emprunt (état réel
  // affiché par le formulaire, pas une donnée simulée) — seul état
  // permettant réellement une sélection menant à un Loan dans ces
  // scénarios déterministes.
  await expect(copyResult).toContainText('AVAILABLE');
  await copyResult.getByRole('button', { name: 'Sélectionner' }).click();

  await page.getByRole('button', { name: 'Enregistrer', exact: true }).click();
  await expect(page.getByText('Prêt enregistré')).toBeVisible();
}

/** Suppose que `/staff/loans` est déjà la page courante (liste rechargée). */
export async function returnLoanViaUi(page: Page, copyInventoryCode: string): Promise<void> {
  const row = page.locator('table tbody tr', { hasText: copyInventoryCode });
  await row.getByRole('button', { name: /Retourner l.exemplaire/ }).click();
  await page.getByRole('button', { name: 'Yes', exact: true }).click();
  await expect(page.getByText('Prêt retourné')).toBeVisible();
}
