import { expect, test } from '@playwright/test';

import { e2eAccounts } from '../fixtures/e2e-accounts';
import { e2eCatalogue } from '../fixtures/e2e-catalogue';
import { getStoredToken, loginAs } from '../fixtures/auth-helpers';
import { createLoanViaUi, returnLoanViaUi } from '../fixtures/loan-helpers';
import { API_BASE } from '../fixtures/e2e-env';


test('un retour rend READY la Reservation WAITING du même Title', async ({ page, request }) => {
  // --- 1. LIBRARIAN prête l'unique Copy du Title A à MEMBER2 (rend le
  // Title réellement indisponible — précondition réelle de Reservation) ---
  await loginAs(page, e2eAccounts.librarian);
  const librarianToken = await getStoredToken(page);
  await createLoanViaUi(page, {
    memberNumber: e2eAccounts.member2.memberNumber,
    titleName: e2eCatalogue.titleA.title,
    copyInventoryCode: e2eCatalogue.titleA.copyInventoryCode,
  });

  const publicTitles = await request.get(`${API_BASE}/titles`, {
    params: { q: e2eCatalogue.titleA.title },
  });
  const titleId: number = (await publicTitles.json()).content[0].id;
  const copiesBeforeReservation = await request.get(`${API_BASE}/staff/titles/${titleId}/copies`, {
    headers: { Authorization: `Bearer ${librarianToken}` },
  });
  const copyBeforeReservation = (await copiesBeforeReservation.json()).find(
    (copy: { inventoryCode: string }) => copy.inventoryCode === e2eCatalogue.titleA.copyInventoryCode,
  );
  expect(copyBeforeReservation.availabilityStatus).toBe('ON_LOAN');

  // --- 2. Create Reservation — UI membre réelle (ReservationCreateDialog) ---
  await loginAs(page, e2eAccounts.member);
  const memberToken = await getStoredToken(page);
  await page.goto('/member/reservations');
  await page.getByRole('button', { name: 'Nouvelle réservation' }).click();

  await page.locator('#reservation-create-title-search').fill(e2eCatalogue.titleA.title);
  const titleResult = page.locator('li', { hasText: e2eCatalogue.titleA.title });
  await expect(titleResult).toBeVisible();
  await titleResult.getByRole('button', { name: 'Sélectionner' }).click();

  await page.getByRole('button', { name: 'Réserver', exact: true }).click();
  await expect(page.getByText('Réservation créée')).toBeVisible();

  // --- 3. Assertions WAITING : UI ---
  // Liste de cartes (DESIGN-V2) : un <article> par réservation, statut en
  // français ("En attente"), jamais l'enum brut ; WAITING → ni échéance ni
  // exemplaire affecté ("Exemplaire —").
  const reservationRow = page.locator('article', { hasText: e2eCatalogue.titleA.title });
  await expect(reservationRow).toBeVisible();
  await expect(reservationRow.getByText('En attente', { exact: true })).toBeVisible();
  await expect(reservationRow).not.toContainText('Échéance');
  await expect(reservationRow).not.toContainText('Disponible jusqu’au');
  await expect(reservationRow).toContainText('Exemplaire —');

  // --- Assertions WAITING : backend réel ---
  const ownReservationsAfterCreate = await request.get(`${API_BASE}/me/reservations`, {
    headers: { Authorization: `Bearer ${memberToken}` },
  });
  expect(ownReservationsAfterCreate.status()).toBe(200);
  const createdReservation = (await ownReservationsAfterCreate.json()).content.find(
    (reservation: { title: { id: number } }) => reservation.title.id === titleId,
  );
  expect(createdReservation).toBeTruthy();
  expect(createdReservation.reservationStatus).toBe('WAITING');
  expect(createdReservation.assignedCopy).toBeNull();
  expect(createdReservation.expirationDate).toBeNull();
  const reservationId: number = createdReservation.id;

  // --- 9. Notification RESERVATION_CREATED : persistée (conséquence
  // systématique de la création, ReservationService#createReservationForUser) ---
  const notificationsAfterCreate = await request.get(`${API_BASE}/me/notifications`, {
    headers: { Authorization: `Bearer ${memberToken}` },
  });
  const createdNotification = (await notificationsAfterCreate.json()).content.find(
    (notification: { notificationType: string; originId: number }) =>
      notification.notificationType === 'RESERVATION_CREATED' && notification.originId === reservationId,
  );
  expect(createdNotification).toBeTruthy();

  // --- 10. Register Return — UI staff réelle (déclencheur naturel du READY,
  // jamais un appel direct à un service de promotion) ---
  await loginAs(page, e2eAccounts.librarian);
  await page.goto('/staff/loans');
  await returnLoanViaUi(page, e2eCatalogue.titleA.copyInventoryCode);

  // --- 11/12/13. Transition WAITING → READY, assignedCopy, expirationDate
  // (backend réel) ---
  const ownReservationsAfterReturn = await request.get(`${API_BASE}/me/reservations`, {
    headers: { Authorization: `Bearer ${memberToken}` },
  });
  const readyReservation = (await ownReservationsAfterReturn.json()).content.find(
    (reservation: { id: number }) => reservation.id === reservationId,
  );
  expect(readyReservation.reservationStatus).toBe('READY');
  expect(readyReservation.assignedCopy.inventoryCode).toBe(e2eCatalogue.titleA.copyInventoryCode);
  expect(readyReservation.expirationDate).not.toBeNull();

  // --- 14. Copy lifecycle : AVAILABLE → ON_LOAN → RESERVED (jamais
  // AVAILABLE au retour, une Reservation WAITING admissible existait) ---
  const copiesAfterReturn = await request.get(`${API_BASE}/staff/titles/${titleId}/copies`, {
    headers: { Authorization: `Bearer ${librarianToken}` },
  });
  const copyAfterReturn = (await copiesAfterReturn.json()).find(
    (copy: { inventoryCode: string }) => copy.inventoryCode === e2eCatalogue.titleA.copyInventoryCode,
  );
  expect(copyAfterReturn.availabilityStatus).toBe('RESERVED');

  // --- 13. expirationDate cohérente avec RESERVATION_READY_HOLD_HOURS réel
  // (jamais 48h codé en dur) — fenêtre de tolérance, pas une comparaison à
  // la seconde près ---
  const adminLogin = await request.post(`${API_BASE}/auth/login`, {
    data: { email: e2eAccounts.admin.email, password: e2eAccounts.admin.password },
  });
  const { token: adminToken } = await adminLogin.json();
  const settingsResponse = await request.get(`${API_BASE}/settings`, {
    headers: { Authorization: `Bearer ${adminToken}` },
  });
  const settings = await settingsResponse.json();
  const holdHours = Number(
    settings.find(
      (setting: { settingKey: string }) => setting.settingKey === 'RESERVATION_READY_HOLD_HOURS',
    ).settingValue,
  );
  const expectedExpirationMs = Date.now() + holdHours * 60 * 60 * 1000;
  const actualExpirationMs = new Date(readyReservation.expirationDate).getTime();
  expect(Math.abs(actualExpirationMs - expectedExpirationMs)).toBeLessThan(5 * 60 * 1000);

  // --- 15. Notification RESERVATION_READY : pour MEMBER (le réservataire),
  // jamais pour MEMBER2 (emprunteur intermédiaire, sans lien avec cette
  // Reservation) ---
  const notificationsAfterReturn = await request.get(`${API_BASE}/me/notifications`, {
    headers: { Authorization: `Bearer ${memberToken}` },
  });
  const readyNotification = (await notificationsAfterReturn.json()).content.find(
    (notification: { notificationType: string; originId: number }) =>
      notification.notificationType === 'RESERVATION_READY' && notification.originId === reservationId,
  );
  expect(readyNotification).toBeTruthy();

  const member2Login = await request.post(`${API_BASE}/auth/login`, {
    data: { email: e2eAccounts.member2.email, password: e2eAccounts.member2.password },
  });
  const { token: member2Token } = await member2Login.json();
  const member2Notifications = await request.get(`${API_BASE}/me/notifications`, {
    headers: { Authorization: `Bearer ${member2Token}` },
  });
  const member2ReadyNotification = (await member2Notifications.json()).content.find(
    (notification: { notificationType: string }) => notification.notificationType === 'RESERVATION_READY',
  );
  expect(member2ReadyNotification).toBeUndefined();
});
