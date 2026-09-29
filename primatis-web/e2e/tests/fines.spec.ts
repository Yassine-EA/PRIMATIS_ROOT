import { expect, test } from '@playwright/test';

import { e2eAccounts } from '../fixtures/e2e-accounts';
import { e2eCatalogue } from '../fixtures/e2e-catalogue';
import { getStoredToken, loginAs } from '../fixtures/auth-helpers';
import { createLoanViaUi, returnLoanViaUi } from '../fixtures/loan-helpers';
import { advanceE2eTime, resetE2eTime } from '../fixtures/e2e-time-helpers';
import { API_BASE } from '../fixtures/e2e-env';


test.afterEach(async ({ request }) => {
  
  // test en échec — aucun test suivant ne doit hériter d'un temps avancé.
  await resetE2eTime(request);
});

test('un retour tardif crée une Fine réelle, bloque un nouveau Loan, puis se débloque après paiement', async ({
  page,
  request,
}) => {
  // --- 1. Loan créé par l'UI staff réelle (Title/Copy dédiés Fine) ---
  await loginAs(page, e2eAccounts.librarian);
  await createLoanViaUi(page, {
    memberNumber: e2eAccounts.member.memberNumber,
    titleName: e2eCatalogue.titleFine.title,
    copyInventoryCode: e2eCatalogue.titleFine.copyInventoryCode,
  });

  const librarianTokenPreAdvance = await getStoredToken(page);
  const loansAfterCreate = await request.get(`${API_BASE}/loans`, {
    headers: { Authorization: `Bearer ${librarianTokenPreAdvance}` },
  });
  const createdLoan = (await loansAfterCreate.json()).content.find(
    (loan: { copy: { inventoryCode: string } }) =>
      loan.copy.inventoryCode === e2eCatalogue.titleFine.copyInventoryCode,
  );
  expect(createdLoan).toBeTruthy();
  const loanId: number = createdLoan.id;

  // --- 2. Lecture des settings réels (jamais 21/0.80/25.00 codés en dur) ---
  const adminLoginPre = await request.post(`${API_BASE}/auth/login`, {
    data: { email: e2eAccounts.admin.email, password: e2eAccounts.admin.password },
  });
  const { token: adminTokenPre } = await adminLoginPre.json();
  const settingsResponse = await request.get(`${API_BASE}/settings`, {
    headers: { Authorization: `Bearer ${adminTokenPre}` },
  });
  const settings: { settingKey: string; settingValue: string }[] = await settingsResponse.json();
  const loanDurationDays = Number(settings.find((s) => s.settingKey === 'LOAN_DURATION_DAYS')!.settingValue);
  const weeklyRate = Number(settings.find((s) => s.settingKey === 'FINE_WEEKLY_RATE')!.settingValue);
  const maxAmount = Number(settings.find((s) => s.settingKey === 'FINE_MAX_AMOUNT')!.settingValue);

  // --- 3. Avance du Clock : dueDate + exactement 1 jour → 1 semaine entamée ---
  // (loanDurationDays + 1) jours en heures : franchit toujours exactement une
  // date calendaire supplémentaire au-delà de dueDate, quelle que soit
  // l'heure courante (UTC, sans DST) — jamais un instant absolu.
  const advanceHours = (loanDurationDays + 1) * 24;
  await advanceE2eTime(request, advanceHours);
  const expectedAmount = Math.min(weeklyRate * 1, maxAmount);

  // --- 4. Return tardif — UI staff réelle. Ré-authentification obligatoire :
  // le JWT capturé à l'étape 1 est désormais invalide (voir note d'en-tête). ---
  await loginAs(page, e2eAccounts.librarian);
  await page.goto('/staff/loans');
  await returnLoanViaUi(page, e2eCatalogue.titleFine.copyInventoryCode);
  const librarianToken = await getStoredToken(page);

  // --- 5. Assertions Return ---
  const loansAfterReturn = await request.get(`${API_BASE}/loans`, {
    headers: { Authorization: `Bearer ${librarianToken}` },
  });
  const returnedLoan = (await loansAfterReturn.json()).content.find((loan: { id: number }) => loan.id === loanId);
  expect(returnedLoan.loanStatus).toBe('RETURNED');
  expect(returnedLoan.returnDate).not.toBeNull();

  // --- 6/7. Fine créée, UNPAID, montant cohérent ---
  const finesResponse = await request.get(`${API_BASE}/fines`, {
    headers: { Authorization: `Bearer ${librarianToken}` },
  });
  const fine = (await finesResponse.json()).content.find((f: { loan: { id: number } }) => f.loan.id === loanId);
  expect(fine).toBeTruthy();
  expect(fine.fineStatus).toBe('UNPAID');
  expect(Number(fine.amount)).toBeCloseTo(expectedAmount, 2);
  const fineId: number = fine.id;
  const memberUserId: number = fine.borrower.id;

  // --- 8. Consultation MEMBER (self-service) ---
  const memberLogin = await request.post(`${API_BASE}/auth/login`, {
    data: { email: e2eAccounts.member.email, password: e2eAccounts.member.password },
  });
  const { token: memberToken } = await memberLogin.json();
  const ownFinesAfterIssue = await request.get(`${API_BASE}/me/fines`, {
    headers: { Authorization: `Bearer ${memberToken}` },
  });
  const ownFine = (await ownFinesAfterIssue.json()).content.find((f: { id: number }) => f.id === fineId);
  expect(ownFine.fineStatus).toBe('UNPAID');

  // --- 9. Notification FINE_ISSUED persistée ---
  const notificationsAfterIssue = await request.get(`${API_BASE}/me/notifications`, {
    headers: { Authorization: `Bearer ${memberToken}` },
  });
  const issuedNotification = (await notificationsAfterIssue.json()).content.find(
    (n: { notificationType: string; originId: number }) =>
      n.notificationType === 'FINE_ISSUED' && n.originId === fineId,
  );
  expect(issuedNotification).toBeTruthy();

  // --- 10. Consultation STAFF — UI réelle ---
  await page.goto('/staff/fines');
  const fineRow = page.locator('table tbody tr', { hasText: e2eCatalogue.titleFine.copyInventoryCode });
  await expect(fineRow).toBeVisible();
  await expect(fineRow.getByText('Impayée', { exact: true })).toBeVisible();

  // --- 11. Blocage nouveau Loan — contrat backend réel (409, code exact) ---
  const publicTitlesFine = await request.get(`${API_BASE}/titles`, {
    params: { q: e2eCatalogue.titleFine.title },
  });
  const titleFineId: number = (await publicTitlesFine.json()).content[0].id;
  const copiesFine = await request.get(`${API_BASE}/staff/titles/${titleFineId}/copies`, {
    headers: { Authorization: `Bearer ${librarianToken}` },
  });
  const fineCopy = (await copiesFine.json()).find(
    (copy: { inventoryCode: string }) => copy.inventoryCode === e2eCatalogue.titleFine.copyInventoryCode,
  );
  // Le Copy est redevenu AVAILABLE au retour (aucune Reservation WAITING sur ce
  // Title) — seule l'éligibilité du membre (Fine UNPAID) doit bloquer le prêt.
  expect(fineCopy.availabilityStatus).toBe('AVAILABLE');

  const blockedAttempt = await request.post(`${API_BASE}/loans`, {
    headers: { Authorization: `Bearer ${librarianToken}` },
    data: { borrowerUserId: memberUserId, copyId: fineCopy.id },
  });
  expect(blockedAttempt.status()).toBe(409);
  const blockedBody = await blockedAttempt.json();
  expect(blockedBody.code).toBe('MEMBER_HAS_UNPAID_FINE');

  // --- 12. Résolution Fine — UI réelle (UNPAID → PAID) ---
  await fineRow.getByRole('button', { name: /Confirmer le paiement de l.amende de E2E Member/ }).click();
  await page.getByRole('button', { name: 'Yes', exact: true }).click();
  await expect(page.getByText('Paiement confirmé')).toBeVisible();
  await expect(fineRow.getByText('Payée', { exact: true })).toBeVisible();

  const finesAfterPayment = await request.get(`${API_BASE}/fines`, {
    headers: { Authorization: `Bearer ${librarianToken}` },
  });
  const paidFine = (await finesAfterPayment.json()).content.find((f: { id: number }) => f.id === fineId);
  expect(paidFine.fineStatus).toBe('PAID');
  expect(paidFine.paidAt).not.toBeNull();

  const notificationsAfterPayment = await request.get(`${API_BASE}/me/notifications`, {
    headers: { Authorization: `Bearer ${memberToken}` },
  });
  const paidNotification = (await notificationsAfterPayment.json()).content.find(
    (n: { notificationType: string; originId: number }) => n.notificationType === 'FINE_PAID' && n.originId === fineId,
  );
  expect(paidNotification).toBeTruthy();

  // --- 13. Déblocage — un nouveau Loan redevient possible, via l'UI réelle,
  // sur le même Copy dédié (redevenu AVAILABLE, jamais RESERVED/ON_LOAN
  // ambigu) ---
  await createLoanViaUi(page, {
    memberNumber: e2eAccounts.member.memberNumber,
    titleName: e2eCatalogue.titleFine.title,
    copyInventoryCode: e2eCatalogue.titleFine.copyInventoryCode,
  });
});
