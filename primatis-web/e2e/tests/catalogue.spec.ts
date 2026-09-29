import { expect, test } from '@playwright/test';

import { e2eCatalogue } from '../fixtures/e2e-catalogue';
import { API_BASE } from '../fixtures/e2e-env';



test.describe('Consultation publique du catalogue', () => {
  test('affiche les Titles ACTIVE de la baseline, jamais le Title WITHDRAWN', async ({ page }) => {
    await page.goto('/catalogue');
    await page.locator('#catalogue-search').fill(e2eCatalogue.searchPrefix);

    // La liste de résultats est une grille de cartes (`<a
    // class="catalogue-v3-card">`, toute la carte est le lien vers le
    // détail, `aria-label="Voir le détail de <titre>"`).
    const cardA = page.locator('.catalogue-v3-card', { hasText: e2eCatalogue.titleA.title });
    const cardB = page.locator('.catalogue-v3-card', { hasText: e2eCatalogue.titleB.title });
    const cardWithoutCopy = page.locator('.catalogue-v3-card', { hasText: e2eCatalogue.titleWithoutCopy.title });
    const cardWithdrawn = page.locator('.catalogue-v3-card', { hasText: e2eCatalogue.titleWithdrawn.title });

    await expect(cardA).toBeVisible();
    await expect(cardB).toBeVisible();
    await expect(cardWithoutCopy).toBeVisible();
    // Le Title WITHDRAWN partage le même préfixe de recherche mais ne doit
    // jamais apparaître : preuve que le filtrage ACTIVE-only est réel côté
    // backend, pas seulement une convention de présentation frontend.
    await expect(cardWithdrawn).toHaveCount(0);
  });

  test('recherche sans résultat affiche un état vide réel (pas un tableau silencieux)', async ({ page }) => {
    await page.goto('/catalogue');
    await page.locator('#catalogue-search').fill('ZZZ_AUCUN_TITRE_E2E_XYZ');

    await expect(page.getByRole('heading', { name: 'Aucun ouvrage trouvé' })).toBeVisible();
  });

  test("détail d'un Title affiche des données réelles cohérentes avec la baseline", async ({ page }) => {
    await page.goto('/catalogue');
    await page.locator('#catalogue-search').fill(e2eCatalogue.titleA.title);

    const card = page.getByRole('link', { name: `Voir le détail de ${e2eCatalogue.titleA.title}` });
    await expect(card).toBeVisible();
    await card.click();

    await expect(page).toHaveURL(/\/catalogue\/\d+$/);
    await expect(page.getByRole('heading', { level: 1, name: e2eCatalogue.titleA.title })).toBeVisible();

    const definitionList = page.locator('dl');
    // DESIGN-V2-B3 : auteurs et genres ne sont plus dans la liste de
    // définitions (bloc d'en-tête `.title-detail-v3-authors` et pastilles de genre).
    await expect(page.locator('.title-detail-v3-authors')).toHaveText(e2eCatalogue.titleA.author);
    await expect(page.locator('.title-detail-v3-genres').getByText(e2eCatalogue.titleA.genre)).toBeVisible();
    // DESIGN-V2-B3 : la langue est affichée en toutes lettres ("Français"),
    // jamais le code brut ("FR") — cf. `TitleDetailPage.languageLabel`.
    
    await expect(definitionList.getByText('Français', { exact: true })).toBeVisible();
    await expect(definitionList.getByText(e2eCatalogue.titleA.isbn)).toBeVisible();
  });

  test("détail d'un Title à deux auteurs affiche les deux noms dans l'ordre alphabétique", async ({ page }) => {
    await page.goto('/catalogue');
    await page.locator('#catalogue-search').fill(e2eCatalogue.titleB.title);

    const card = page.getByRole('link', { name: `Voir le détail de ${e2eCatalogue.titleB.title}` });
    await expect(card).toBeVisible();
    await card.click();

    await expect(page.getByRole('heading', { level: 1, name: e2eCatalogue.titleB.title })).toBeVisible();
    await expect(page.locator('.title-detail-v3-authors')).toHaveText(e2eCatalogue.titleB.authors.join(', '));
  });

  test('visiteur anonyme : CTA "Se connecter pour réserver" avec returnUrl réel, puis retour au Catalogue via le fil d\'Ariane (DESIGN-V2-B3)', async ({
    page,
  }) => {
    await page.goto('/catalogue');
    await page.getByRole('link', { name: `Voir le détail de ${e2eCatalogue.titleA.title}` }).click();
    await expect(page).toHaveURL(/\/catalogue\/\d+$/);
    const detailUrl = page.url();

    // Aucune disponibilité inventée, aucun bouton "Réserver" pour un visiteur anonyme.
    await expect(page.getByRole('button', { name: 'Réserver', exact: true })).toHaveCount(0);
    const loginCta = page.getByRole('link', { name: 'Se connecter pour réserver' });
    await expect(loginCta).toBeVisible();
    await loginCta.click();

    await expect(page).toHaveURL(/\/login\?returnUrl=/);
    expect(decodeURIComponent(page.url())).toContain(new URL(detailUrl).pathname);

    
    // Scope explicite : "Catalogue" apparaît aussi dans la navigation du header.
    await page.goto(detailUrl);
    await page.locator('.title-detail-v3-breadcrumb').getByRole('link', { name: 'Catalogue', exact: true }).click();
    await expect(page).toHaveURL('/catalogue');
  });

  test('une navigation directe avec ?q=... initialise le champ, filtre réellement, et le reset fonctionne (DESIGN-V2-B2)', async ({
    page,
  }) => {
    await page.goto(`/catalogue?q=${encodeURIComponent(e2eCatalogue.titleA.title)}`);

    await expect(page.locator('#catalogue-search')).toHaveValue(e2eCatalogue.titleA.title);
    await expect(page.locator('.catalogue-v3-card', { hasText: e2eCatalogue.titleA.title })).toBeVisible();
    await expect(page.locator('.catalogue-v3-card', { hasText: e2eCatalogue.titleB.title })).toHaveCount(0);

    await page.getByRole('button', { name: 'Effacer la recherche' }).click();

    await expect(page.locator('#catalogue-search')).toHaveValue('');
    await expect(page).toHaveURL('/catalogue');
    await expect(page.locator('.catalogue-v3-card', { hasText: e2eCatalogue.titleB.title })).toBeVisible();
  });

  test('une recherche par nom d\'auteur retourne les Titles de cet auteur (recherche titre OU auteur)', async ({
    page,
  }) => {
    await page.goto('/catalogue');
    // "E2E Author Beta" n'est co-auteur que de titleB (jamais de titleA) —
    // preuve que la recherche q porte réellement sur l'auteur, pas
    // seulement sur le titre (aucun des deux titres ne contient ce nom).
    await page.locator('#catalogue-search').fill('E2E Author Beta');

    await expect(page.locator('.catalogue-v3-card', { hasText: e2eCatalogue.titleB.title })).toBeVisible();
    await expect(page.locator('.catalogue-v3-card', { hasText: e2eCatalogue.titleA.title })).toHaveCount(0);
  });

  test('le filtre Genre restreint les résultats au Genre sélectionné, sans le coder en dur (options chargées depuis le backend)', async ({
    page,
  }) => {
    await page.goto('/catalogue');
    await page.locator('#catalogue-search').fill(e2eCatalogue.searchPrefix);

    await page.getByLabel('Genre').click();
    await page.getByRole('option', { name: e2eCatalogue.titleB.genre }).click();

    await expect(page.locator('.catalogue-v3-card', { hasText: e2eCatalogue.titleB.title })).toBeVisible();
    await expect(page.locator('.catalogue-v3-card', { hasText: e2eCatalogue.titleA.title })).toHaveCount(0);
    await expect(page).toHaveURL(/genreCode=E2E_ESSAY/);

    await page.getByRole('button', { name: 'Réinitialiser' }).click();

    await expect(page.locator('.catalogue-v3-card', { hasText: e2eCatalogue.titleA.title })).toBeVisible();
    await expect(page).not.toHaveURL(/genreCode=/);
  });

  test('le champ de recherche affiche un message honnête sur son périmètre réel (titre et auteur)', async ({
    page,
  }) => {
    await page.goto('/catalogue');

    await expect(page.getByText("La recherche porte sur le titre et l'auteur des ouvrages.")).toBeVisible();
    await expect(page.getByRole('link', { name: 'Filtrer par langue' })).toHaveCount(0);
  });
});


test.describe('Mode d\'affichage Cartes / Liste', () => {
  test.use({ viewport: { width: 1280, height: 900 } });

  test('Cartes est le mode par défaut, la Liste reste accessible et le retour fonctionne', async ({ page }) => {
    await page.goto('/catalogue');
    await page.locator('#catalogue-search').fill(e2eCatalogue.searchPrefix);

    await expect(page.locator('.catalogue-v3-grid')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Cartes' })).toHaveAttribute('aria-pressed', 'true');
    await expect(page.getByRole('button', { name: 'Liste' })).toHaveAttribute('aria-pressed', 'false');

    await page.getByRole('button', { name: 'Liste' }).click();

    await expect(page.locator('.catalogue-v3-list')).toBeVisible();
    await expect(page.locator('.catalogue-v3-grid')).toHaveCount(0);
    await expect(page.getByRole('button', { name: 'Liste' })).toHaveAttribute('aria-pressed', 'true');
    await expect(page.locator('.catalogue-v3-row', { hasText: e2eCatalogue.titleA.title })).toBeVisible();

    await page.getByRole('button', { name: 'Cartes' }).click();

    await expect(page.locator('.catalogue-v3-grid')).toBeVisible();
    await expect(page.locator('.catalogue-v3-list')).toHaveCount(0);
  });

  test('le lien "Voir la fiche" mène au détail du Title en mode Liste', async ({ page }) => {
    await page.goto('/catalogue');
    await page.locator('#catalogue-search').fill(e2eCatalogue.titleA.title);
    await page.getByRole('button', { name: 'Liste' }).click();

    const row = page.getByRole('link', { name: `Voir le détail de ${e2eCatalogue.titleA.title}` });
    await expect(row).toBeVisible();
    await row.click();

    // Vérifie uniquement que le lien de la ligne Liste navigue vers la
    // bonne fiche (comportement propre à ce chantier) — ne réaffirme pas
    // le rendu de `title-detail-page` elle-même : cette page porte un bug
    
    // touchée par VIEW-1/VIEW-2), déjà confirmé par `catalogue.spec.ts`
    // `détail d'un Title affiche des données réelles cohérentes...`.
    await expect(page).toHaveURL(/\/catalogue\/\d+$/);
  });

  test('les filtres Langue/Genre et la recherche restent fonctionnels en mode Liste', async ({ page }) => {
    await page.goto('/catalogue');
    await page.locator('#catalogue-search').fill(e2eCatalogue.searchPrefix);
    await page.getByRole('button', { name: 'Liste' }).click();

    await page.getByLabel('Genre').click();
    await page.getByRole('option', { name: e2eCatalogue.titleB.genre }).click();

    await expect(page.locator('.catalogue-v3-row', { hasText: e2eCatalogue.titleB.title })).toBeVisible();
    await expect(page.locator('.catalogue-v3-row', { hasText: e2eCatalogue.titleA.title })).toHaveCount(0);
  });
});


test.describe('Persistance URL vue/taille (DEV-CATALOGUE-VIEW-2)', () => {
  test.use({ viewport: { width: 1280, height: 900 } });

  test('une URL directe ?view=list ouvre la vue Liste', async ({ page }) => {
    await page.goto('/catalogue?view=list');

    await expect(page.locator('.catalogue-v3-list')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Liste' })).toHaveAttribute('aria-pressed', 'true');
  });

  test('une URL directe ?size=40 demande réellement 40 résultats au backend', async ({ page }) => {
    const request = page.waitForRequest(
      (req) => req.url().includes('/api/v1/titles') && req.url().includes('size=40'),
    );
    await page.goto('/catalogue?size=40');
    await request;

    await expect(page.locator('.catalogue-v3-page-size-select')).toContainText('40 par page');
  });

  test('changer la taille de page revient à la page 0 et déclenche une seule requête cohérente', async ({
    page,
  }) => {
    await page.goto('/catalogue');
    await page.waitForSelector('.catalogue-v3-card');

    const request = page.waitForRequest(
      (req) => req.url().includes('/api/v1/titles') && req.url().includes('size=60') && req.url().includes('page=0'),
    );
    await page.locator('#catalogue-page-size').click();
    await page.getByRole('option', { name: '60 par page' }).click();
    await request;

    await expect(page).toHaveURL(/size=60/);
  });

  test('combine q + view + size dans l\'URL, et la Liste reste filtrée', async ({ page }) => {
    await page.goto('/catalogue');
    await page.locator('#catalogue-search').fill(e2eCatalogue.searchPrefix);
    await page.getByRole('button', { name: 'Liste' }).click();
    await page.locator('#catalogue-page-size').click();
    await page.getByRole('option', { name: '40 par page' }).click();
    await page.waitForTimeout(300);

    await expect(page).toHaveURL(/view=list/);
    await expect(page).toHaveURL(/size=40/);
    expect(decodeURIComponent(page.url())).toContain(`q=${e2eCatalogue.searchPrefix}`);
    await expect(page.locator('.catalogue-v3-row', { hasText: e2eCatalogue.titleA.title })).toBeVisible();
  });

  test('le reset des filtres conserve view et size dans l\'URL', async ({ page }) => {
    await page.goto('/catalogue');
    await page.locator('#catalogue-search').fill(e2eCatalogue.searchPrefix);
    await page.getByRole('button', { name: 'Liste' }).click();
    await page.locator('#catalogue-page-size').click();
    await page.getByRole('option', { name: '40 par page' }).click();
    await page.waitForTimeout(300);

    await page.getByRole('button', { name: 'Réinitialiser' }).click();
    await page.waitForTimeout(300);

    await expect(page).not.toHaveURL(/q=/);
    await expect(page).toHaveURL(/view=list/);
    await expect(page).toHaveURL(/size=40/);
    await expect(page.locator('.catalogue-v3-list')).toBeVisible();
  });

  test('retour au mode Cartes retire view= de l\'URL (défaut jamais écrit)', async ({ page }) => {
    await page.goto('/catalogue?view=list');
    await expect(page.locator('.catalogue-v3-list')).toBeVisible();

    await page.getByRole('button', { name: 'Cartes' }).click();

    await expect(page).not.toHaveURL(/view=/);
    await expect(page.locator('.catalogue-v3-grid')).toBeVisible();
  });
});

test.describe('API publique du catalogue (contrat HTTP réel, sans JWT)', () => {
  test('GET /api/v1/titles répond 200 sans authentification et ne contient jamais un WITHDRAWN', async ({
    request,
  }) => {
    const response = await request.get(`${API_BASE}/titles`, {
      params: { q: e2eCatalogue.searchPrefix },
    });

    expect(response.status()).toBe(200);
    const body = await response.json();

    const isbns: string[] = body.content.map((title: { isbn: string }) => title.isbn);
    expect(isbns).toContain(e2eCatalogue.titleA.isbn);
    expect(isbns).toContain(e2eCatalogue.titleB.isbn);
    expect(isbns).toContain(e2eCatalogue.titleWithoutCopy.isbn);
    expect(isbns).not.toContain(e2eCatalogue.titleWithdrawn.isbn);

    const statuses: string[] = body.content.map((title: { titleStatus: string }) => title.titleStatus);
    expect(statuses.every((status) => status === 'ACTIVE')).toBe(true);
  });

  test('GET /api/v1/genres répond 200 sans authentification et expose les Genres réels de la baseline', async ({
    request,
  }) => {
    const response = await request.get(`${API_BASE}/genres`);

    expect(response.status()).toBe(200);
    const body: Array<{ code: string; label: string }> = await response.json();

    expect(body.map((genre) => genre.code)).toContain('E2E_NOVEL');
    expect(body.map((genre) => genre.code)).toContain('E2E_ESSAY');
  });
});
