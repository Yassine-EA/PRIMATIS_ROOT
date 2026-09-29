import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { BehaviorSubject, of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { PageResponse } from '../../../core/models/page-response';
import { GenreResponse } from '../../models/genre-response';
import { TitleResponse } from '../../models/title-response';
import { TitleSearchParams } from '../../models/title-search-params';
import { CatalogueApiService } from '../../services/catalogue-api.service';
import { CataloguePage } from './catalogue-page';

function buildGenre(overrides: Partial<GenreResponse> = {}): GenreResponse {
  return { id: 1, code: 'FICTION', label: 'Fiction', description: null, ...overrides };
}

function buildTitle(overrides: Partial<TitleResponse> = {}): TitleResponse {
  return {
    id: 1,
    isbn: null,
    title: 'Les Misérables',
    subtitle: null,
    publicationYear: 1862,
    language: 'FR',
    publisher: null,
    coverImageUrl: null,
    titleStatus: 'ACTIVE',
    authorNames: [],
    availableCopies: 0,
    ...overrides,
  };
}

function buildPage(
  content: TitleResponse[],
  totalElements = content.length,
): PageResponse<TitleResponse> {
  return {
    content,
    page: 0,
    size: 20,
    totalElements,
    totalPages: Math.max(1, Math.ceil(totalElements / 20)),
  };
}

function apiHttpError(code: string, message: string): HttpErrorResponse {
  return new HttpErrorResponse({
    status: 500,
    error: {
      timestamp: new Date().toISOString(),
      status: 500,
      error: 'Internal Server Error',
      code,
      message,
      path: '/api/v1/titles',
      fieldErrors: [],
    },
  });
}

describe('CataloguePage', () => {
  let fixture: ComponentFixture<CataloguePage>;
  let component: CataloguePage;
  let catalogueApiServiceMock: {
    searchTitles: ReturnType<typeof vi.fn>;
    listGenres: ReturnType<typeof vi.fn>;
  };
  let queryParamMap$: BehaviorSubject<ReturnType<typeof convertToParamMap>>;
  let navigateSpy: ReturnType<typeof vi.spyOn>;

  function configure(initialQuery: Record<string, string> = {}): void {
    TestBed.resetTestingModule();
    queryParamMap$ = new BehaviorSubject(convertToParamMap(initialQuery));
    catalogueApiServiceMock = {
      searchTitles: vi.fn().mockReturnValue(of(buildPage([buildTitle()]))),
      listGenres: vi.fn().mockReturnValue(of([buildGenre()])),
    };

    TestBed.configureTestingModule({
      imports: [CataloguePage],
      providers: [
        provideRouter([]),
        { provide: CatalogueApiService, useValue: catalogueApiServiceMock },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { queryParamMap: queryParamMap$.value },
            queryParamMap: queryParamMap$,
          },
        },
      ],
    });

    const router = TestBed.inject(Router);
    navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);
  }

  function createComponent(): void {
    fixture = TestBed.createComponent(CataloguePage);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  function lastParams(): TitleSearchParams {
    return catalogueApiServiceMock.searchTitles.mock.calls.at(-1)?.[0] as TitleSearchParams;
  }

  beforeEach(() => {
    vi.useFakeTimers();
    configure();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('should load Titles on initial construction with page=0 and the default size', () => {
    createComponent();

    expect(catalogueApiServiceMock.searchTitles).toHaveBeenCalledTimes(1);
    expect(lastParams()).toEqual({ page: 0, size: 20 });
  });

  it('should display the Titles received from the API', () => {
    catalogueApiServiceMock.searchTitles.mockReturnValue(
      of(
        buildPage([
          buildTitle({ id: 1, title: 'Les Misérables' }),
          buildTitle({ id: 2, title: 'Germinal' }),
        ]),
      ),
    );
    createComponent();

    expect(component.rows().map((row) => row.title)).toEqual(['Les Misérables', 'Germinal']);
    expect(component.totalRecords()).toBe(2);
  });

  it('should reload with the requested page/rows on paginator page change', () => {
    createComponent();
    catalogueApiServiceMock.searchTitles.mockClear();

    component.onPageChange({ first: 40, rows: 20, page: 2, pageCount: 5 });

    expect(lastParams()).toEqual({ page: 2, size: 20 });
  });

  it('should fall back to defaults when the page change event omits first/rows', () => {
    createComponent();
    catalogueApiServiceMock.searchTitles.mockClear();

    component.onPageChange({});

    expect(lastParams()).toEqual({ page: 0, size: 20 });
  });

  it('should debounce the q filter and send it trimmed after the search request settles', () => {
    createComponent();
    catalogueApiServiceMock.searchTitles.mockClear();

    component.filtersForm.controls.q.setValue('  Misérables  ');
    vi.advanceTimersByTime(299);
    expect(catalogueApiServiceMock.searchTitles).not.toHaveBeenCalled();

    vi.advanceTimersByTime(1);
    expect(lastParams()).toEqual({ page: 0, size: 20, q: 'Misérables' });
  });

  it('should not send q when the trimmed value is empty', () => {
    createComponent();
    catalogueApiServiceMock.searchTitles.mockClear();

    component.filtersForm.controls.q.setValue('   ');
    vi.advanceTimersByTime(300);

    expect(lastParams()).toEqual({ page: 0, size: 20 });
  });

  it('should reset to page 0 when the q filter changes after paginating', () => {
    createComponent();
    component.onPageChange({ first: 40, rows: 20 });
    catalogueApiServiceMock.searchTitles.mockClear();

    component.filtersForm.controls.q.setValue('Zola');
    vi.advanceTimersByTime(300);

    expect(lastParams()).toEqual({ page: 0, size: 20, q: 'Zola' });
  });

  it('should send the language filter immediately without debounce', () => {
    createComponent();
    catalogueApiServiceMock.searchTitles.mockClear();

    component.filtersForm.controls.language.setValue('FR');

    expect(lastParams()).toEqual({ page: 0, size: 20, language: 'FR' });
  });

  it('should reset to page 0 when the language filter changes after paginating', () => {
    createComponent();
    component.onPageChange({ first: 40, rows: 20 });
    catalogueApiServiceMock.searchTitles.mockClear();

    component.filtersForm.controls.language.setValue('EN');

    expect(lastParams()).toEqual({ page: 0, size: 20, language: 'EN' });
  });

  it('should omit the language filter again when reset to "Toutes les langues" (null)', () => {
    createComponent();
    component.filtersForm.controls.language.setValue('FR');
    catalogueApiServiceMock.searchTitles.mockClear();

    component.filtersForm.controls.language.setValue(null);

    expect(lastParams()).toEqual({ page: 0, size: 20 });
  });

  it('should send the genre filter immediately without debounce', () => {
    createComponent();
    catalogueApiServiceMock.searchTitles.mockClear();

    component.filtersForm.controls.genreCode.setValue('FICTION');

    expect(lastParams()).toEqual({ page: 0, size: 20, genreCode: 'FICTION' });
  });

  it('should reset to page 0 when the genre filter changes after paginating', () => {
    createComponent();
    component.onPageChange({ first: 40, rows: 20 });
    catalogueApiServiceMock.searchTitles.mockClear();

    component.filtersForm.controls.genreCode.setValue('FANTASY');

    expect(lastParams()).toEqual({ page: 0, size: 20, genreCode: 'FANTASY' });
  });

  it('should omit the genre filter again when reset to "Tous les genres" (null)', () => {
    createComponent();
    component.filtersForm.controls.genreCode.setValue('FICTION');
    catalogueApiServiceMock.searchTitles.mockClear();

    component.filtersForm.controls.genreCode.setValue(null);

    expect(lastParams()).toEqual({ page: 0, size: 20 });
  });

  it('should combine q, language and genre filters in a single request', () => {
    createComponent();
    catalogueApiServiceMock.searchTitles.mockClear();

    component.filtersForm.controls.language.setValue('FR');
    component.filtersForm.controls.genreCode.setValue('FICTION');
    component.filtersForm.controls.q.setValue('Zola');
    vi.advanceTimersByTime(300);

    expect(lastParams()).toEqual({
      page: 0,
      size: 20,
      q: 'Zola',
      language: 'FR',
      genreCode: 'FICTION',
    });
  });

  it('should populate genreOptions from the backend, prefixed with "Tous les genres"', () => {
    catalogueApiServiceMock.listGenres.mockReturnValue(
      of([
        buildGenre({ code: 'FICTION', label: 'Fiction' }),
        buildGenre({ code: 'FANTASY', label: 'Fantasy' }),
      ]),
    );
    createComponent();

    expect(component.genreOptions()).toEqual([
      { label: 'Tous les genres', value: null },
      { label: 'Fiction', value: 'FICTION' },
      { label: 'Fantasy', value: 'FANTASY' },
    ]);
  });

  it('should keep the active q filter across a page change', () => {
    createComponent();
    component.filtersForm.controls.q.setValue('Zola');
    vi.advanceTimersByTime(300);
    catalogueApiServiceMock.searchTitles.mockClear();

    component.onPageChange({ first: 20, rows: 20 });

    expect(lastParams()).toEqual({ page: 1, size: 20, q: 'Zola' });
  });

  it('should show the loading state before the first response arrives', () => {
    // Observable never emits synchronously: keeps the component in its initial loading state.
    catalogueApiServiceMock.searchTitles.mockReturnValue({
      subscribe: () => ({ unsubscribe: () => {} }),
    });
    createComponent();

    expect(component.loading()).toBe(true);
    expect(fixture.nativeElement.querySelector('app-loading-state')).not.toBeNull();
  });

  it('should show the empty state when no Title matches and there is no error', () => {
    catalogueApiServiceMock.searchTitles.mockReturnValue(of(buildPage([])));
    createComponent();

    expect(fixture.nativeElement.textContent).toContain('Aucun ouvrage trouvé');
  });

  it('should show the error state when the request fails', () => {
    catalogueApiServiceMock.searchTitles.mockReturnValue(
      throwError(() => apiHttpError('INTERNAL_ERROR', 'Erreur serveur.')),
    );
    createComponent();

    expect(component.error()?.message).toBe('Erreur serveur.');
    expect(fixture.nativeElement.textContent).toContain('Erreur serveur.');
  });

  it('should retry the last page/size when retry() is called', () => {
    catalogueApiServiceMock.searchTitles.mockReturnValue(
      throwError(() => apiHttpError('INTERNAL_ERROR', 'Erreur serveur.')),
    );
    createComponent();
    component.onPageChange({ first: 40, rows: 20 });
    catalogueApiServiceMock.searchTitles.mockClear();
    catalogueApiServiceMock.searchTitles.mockReturnValue(of(buildPage([buildTitle()])));

    component.retry();

    expect(lastParams()).toEqual({ page: 2, size: 20 });
    expect(component.error()).toBeNull();
  });

  it('should render a link to the Title detail page', () => {
    catalogueApiServiceMock.searchTitles.mockReturnValue(of(buildPage([buildTitle({ id: 42 })])));
    createComponent();

    const link: HTMLAnchorElement | null =
      fixture.nativeElement.querySelector('a[href="/catalogue/42"]');
    expect(link).not.toBeNull();
  });

  describe('auteurs et disponibilité (DEV-16.7 FIX-03)', () => {
    const LONG_TITLE =
      'Une très longue histoire de la manière dont les bibliothèques publiques de Belgique ont traversé les siècles';

    function renderRows(
      rows: ReturnType<typeof buildTitle>[],
      view: 'grid' | 'list' = 'grid',
    ): void {
      catalogueApiServiceMock.searchTitles.mockReturnValue(of(buildPage(rows)));
      createComponent();
      if (view === 'list') {
        component.setViewMode('list');
        fixture.detectChanges();
      }
    }

    function text(selector: string): string {
      return (fixture.nativeElement.querySelector(selector)?.textContent ?? '')
        .replace(/\s+/g, ' ')
        .trim();
    }

    it('should show the author under the title on a card', () => {
      renderRows([buildTitle({ authorNames: ['Victor Hugo'] })]);

      expect(text('.catalogue-v3-card .catalogue-v3-authors')).toBe('Victor Hugo');
    });

    it('should not render an author line when the Title has no author (nullable)', () => {
      renderRows([buildTitle({ authorNames: [] })]);

      expect(fixture.nativeElement.querySelector('.catalogue-v3-authors')).toBeNull();
    });

    it('should compact more than two authors and keep the full list in the title attribute', () => {
      renderRows([buildTitle({ authorNames: ['A. Un', 'B. Deux', 'C. Trois', 'D. Quatre'] })]);

      const authors = fixture.nativeElement.querySelector('.catalogue-v3-authors') as HTMLElement;
      expect(authors.textContent?.trim()).toBe('A. Un, B. Deux et 2 autres');
      expect(authors.getAttribute('title')).toBe('A. Un, B. Deux, C. Trois, D. Quatre');
    });

    it('should use the singular when exactly one author is hidden', () => {
      expect(component.authorsLabel(buildTitle({ authorNames: ['A', 'B', 'C'] }))).toBe(
        'A, B et 1 autre',
      );
    });

    it('should show "Disponible" with an icon when availableCopies > 0', () => {
      renderRows([buildTitle({ availableCopies: 3 })]);

      const badge = fixture.nativeElement.querySelector(
        '.catalogue-v3-availability',
      ) as HTMLElement;
      expect(badge.textContent?.trim()).toBe('Disponible');
      expect(badge.classList.contains('catalogue-v3-availability-yes')).toBe(true);
      expect(badge.querySelector('.pi-check-circle')).not.toBeNull();
    });

    it('should show "Indisponible" without the available style when availableCopies is 0', () => {
      renderRows([buildTitle({ availableCopies: 0 })]);

      const badge = fixture.nativeElement.querySelector(
        '.catalogue-v3-availability',
      ) as HTMLElement;
      expect(badge.textContent?.trim()).toBe('Indisponible');
      expect(badge.classList.contains('catalogue-v3-availability-yes')).toBe(false);
      expect(badge.querySelector('.pi-minus-circle')).not.toBeNull();
    });

    it('should expose author and availability in the accessible name of the link', () => {
      renderRows([
        buildTitle({
          id: 42,
          title: 'Les Misérables',
          authorNames: ['Victor Hugo'],
          availableCopies: 1,
        }),
      ]);

      const link = fixture.nativeElement.querySelector(
        'a[href="/catalogue/42"]',
      ) as HTMLAnchorElement;
      expect(link.getAttribute('aria-label')).toBe(
        'Voir le détail de Les Misérables, Victor Hugo — Disponible',
      );
    });

    it('should keep the full long title reachable through the title attribute', () => {
      renderRows([buildTitle({ title: LONG_TITLE })]);

      const title = fixture.nativeElement.querySelector('.catalogue-v3-card-title') as HTMLElement;
      expect(title.getAttribute('title')).toBe(LONG_TITLE);
      expect(title.textContent?.trim()).toBe(LONG_TITLE);
    });

    it('should render the richer list row: authors, language, year, publisher, ISBN and availability', () => {
      renderRows(
        [
          buildTitle({
            id: 7,
            title: LONG_TITLE,
            subtitle: 'Édition revue',
            authorNames: ['Victor Hugo', 'Auguste Vacquerie'],
            language: 'FR',
            publicationYear: 1862,
            publisher: 'Gallimard',
            isbn: '9782070409228',
            availableCopies: 2,
          }),
        ],
        'list',
      );

      expect(text('.catalogue-v3-row .catalogue-v3-authors')).toBe(
        'Victor Hugo, Auguste Vacquerie',
      );
      expect(text('.catalogue-v3-row-title')).toContain('Édition revue');
      expect(text('.catalogue-v3-row-biblio')).toContain('1862');
      expect(text('.catalogue-v3-row-biblio')).toContain('Gallimard');
      expect(text('.catalogue-v3-row-biblio')).toContain('ISBN 9782070409228');
      expect(text('.catalogue-v3-row-availability')).toBe('Disponible');
      expect(text('.catalogue-v3-row-link')).toBe('Voir la fiche');
    });

    it('should omit the optional list metadata when publisher, year and ISBN are null', () => {
      renderRows([buildTitle({ publisher: null, publicationYear: null, isbn: null })], 'list');

      expect(fixture.nativeElement.querySelector('.catalogue-v3-row-publisher')).toBeNull();
      expect(fixture.nativeElement.querySelector('.catalogue-v3-row-isbn')).toBeNull();
    });

    it('should not show the publisher on the card (kept to the list view)', () => {
      renderRows([buildTitle({ publisher: 'Gallimard' })]);

      expect(fixture.nativeElement.textContent).not.toContain('Gallimard');
    });

    it('should show the same data and no extra request when switching between grid and list', () => {
      renderRows([buildTitle({ authorNames: ['Victor Hugo'], availableCopies: 1 })]);
      catalogueApiServiceMock.searchTitles.mockClear();

      component.setViewMode('list');
      fixture.detectChanges();

      expect(text('.catalogue-v3-row .catalogue-v3-authors')).toBe('Victor Hugo');
      expect(text('.catalogue-v3-row-availability')).toBe('Disponible');
      expect(catalogueApiServiceMock.searchTitles).not.toHaveBeenCalled();
    });

    it('should keep the fallback cover in the card when coverImageUrl is null', () => {
      renderRows([buildTitle({ coverImageUrl: null })]);

      expect(fixture.nativeElement.querySelector('.catalogue-v3-cover-fallback')).not.toBeNull();
    });
  });

  describe('view mode (DEV-CATALOGUE-VIEW-1)', () => {
    it('should default to the grid (Cartes) view', () => {
      createComponent();

      expect(component.viewMode()).toBe('grid');
      expect(fixture.nativeElement.querySelector('.catalogue-v3-grid')).not.toBeNull();
      expect(fixture.nativeElement.querySelector('.catalogue-v3-list')).toBeNull();
    });

    it('should switch to the list (Liste) view when setViewMode("list") is called', () => {
      createComponent();

      component.setViewMode('list');
      fixture.detectChanges();

      expect(component.viewMode()).toBe('list');
      expect(fixture.nativeElement.querySelector('.catalogue-v3-list')).not.toBeNull();
      expect(fixture.nativeElement.querySelector('.catalogue-v3-grid')).toBeNull();
    });

    it('should switch back to the grid view when setViewMode("grid") is called after list', () => {
      createComponent();
      component.setViewMode('list');
      fixture.detectChanges();

      component.setViewMode('grid');
      fixture.detectChanges();

      expect(component.viewMode()).toBe('grid');
      expect(fixture.nativeElement.querySelector('.catalogue-v3-grid')).not.toBeNull();
      expect(fixture.nativeElement.querySelector('.catalogue-v3-list')).toBeNull();
    });

    it('should not reload data when the view mode changes', () => {
      createComponent();
      catalogueApiServiceMock.searchTitles.mockClear();

      component.setViewMode('list');
      fixture.detectChanges();

      expect(catalogueApiServiceMock.searchTitles).not.toHaveBeenCalled();
    });

    it('should render a link to the Title detail page in list view', () => {
      catalogueApiServiceMock.searchTitles.mockReturnValue(of(buildPage([buildTitle({ id: 42 })])));
      createComponent();

      component.setViewMode('list');
      fixture.detectChanges();

      const link: HTMLAnchorElement | null =
        fixture.nativeElement.querySelector('a[href="/catalogue/42"]');
      expect(link).not.toBeNull();
      expect(link?.classList.contains('catalogue-v3-row')).toBe(true);
    });

    it('should mark the active view toggle button with aria-pressed="true"', () => {
      createComponent();

      const buttons: HTMLButtonElement[] = Array.from(
        fixture.nativeElement.querySelectorAll('.catalogue-v3-view-toggle-btn'),
      );
      expect(buttons).toHaveLength(2);
      expect(buttons[0].getAttribute('aria-pressed')).toBe('true');
      expect(buttons[1].getAttribute('aria-pressed')).toBe('false');

      buttons[1].click();
      fixture.detectChanges();

      expect(buttons[0].getAttribute('aria-pressed')).toBe('false');
      expect(buttons[1].getAttribute('aria-pressed')).toBe('true');
    });

    it('should show a fallback cover in list view when coverImageUrl is null', () => {
      catalogueApiServiceMock.searchTitles.mockReturnValue(
        of(buildPage([buildTitle({ coverImageUrl: null })])),
      );
      createComponent();
      component.setViewMode('list');
      fixture.detectChanges();

      expect(
        fixture.nativeElement.querySelector('.catalogue-card-cover .cover-fallback'),
      ).not.toBeNull();
      expect(fixture.nativeElement.querySelector('.catalogue-card-cover img')).toBeNull();
    });
  });

  it('should set the document title (DEV-15.5)', () => {
    createComponent();

    expect(document.title).toBe('Catalogue — PRIMATIS');
  });

  it('should show a fallback cover when coverImageUrl is null', () => {
    catalogueApiServiceMock.searchTitles.mockReturnValue(
      of(buildPage([buildTitle({ coverImageUrl: null })])),
    );
    createComponent();

    expect(
      fixture.nativeElement.querySelector('.catalogue-card-cover .cover-fallback'),
    ).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.catalogue-card-cover img')).toBeNull();
  });

  it('should render the real cover image when coverImageUrl is present', () => {
    catalogueApiServiceMock.searchTitles.mockReturnValue(
      of(buildPage([buildTitle({ coverImageUrl: 'https://covers.example/1.jpg' })])),
    );
    createComponent();

    const img: HTMLImageElement | null = fixture.nativeElement.querySelector(
      '.catalogue-card-cover img',
    );
    expect(img?.getAttribute('src')).toBe('https://covers.example/1.jpg');
  });

  it('should clear the q filter and reload when clearSearch() is called', () => {
    createComponent();
    component.filtersForm.controls.q.setValue('Zola');
    vi.advanceTimersByTime(300);
    catalogueApiServiceMock.searchTitles.mockClear();

    component.clearSearch();
    vi.advanceTimersByTime(300);

    expect(component.filtersForm.controls.q.value).toBe('');
    expect(lastParams()).toEqual({ page: 0, size: 20 });
  });

  it('should show the clear button only when a search term is present', () => {
    createComponent();
    expect(fixture.nativeElement.querySelector('.catalogue-search-clear')).toBeNull();

    component.filtersForm.controls.q.setValue('Zola');
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.catalogue-search-clear')).not.toBeNull();
  });

  it('should reload immediately on form submission, without waiting for the debounce', () => {
    createComponent();
    component.filtersForm.controls.q.setValue('Zola');
    catalogueApiServiceMock.searchTitles.mockClear();

    component.onSubmit();

    expect(lastParams()).toEqual({ page: 0, size: 20, q: 'Zola' });
  });

  it('should reset q, language and genre when resetFilters() is called', () => {
    createComponent();
    component.filtersForm.controls.q.setValue('Zola');
    component.filtersForm.controls.language.setValue('FR');
    component.filtersForm.controls.genreCode.setValue('FICTION');
    vi.advanceTimersByTime(300);

    component.resetFilters();
    vi.advanceTimersByTime(300);

    expect(component.filtersForm.controls.q.value).toBe('');
    expect(component.filtersForm.controls.language.value).toBeNull();
    expect(component.filtersForm.controls.genreCode.value).toBeNull();
  });

  it('should navigate with the fully-cleared state on the last call when resetFilters() combines 3 filters (regression VR27: a real-router bug — race between 3 separate navigate() calls left stale query params after reset — was invisible here until this test sent the full state per call instead of a single changed field)', () => {
    createComponent();
    component.filtersForm.controls.q.setValue('Zola');
    component.filtersForm.controls.language.setValue('FR');
    component.filtersForm.controls.genreCode.setValue('FICTION');
    vi.advanceTimersByTime(300);
    navigateSpy.mockClear();

    component.resetFilters();
    vi.advanceTimersByTime(300);

    const lastCall = navigateSpy.mock.calls.at(-1);
    expect(lastCall?.[1]).toEqual(
      expect.objectContaining({
        queryParams: { q: null, language: null, genreCode: null, view: null, size: null },
      }),
    );
  });

  it('should hide the "Réinitialiser" button when no filter is active', () => {
    createComponent();

    expect(fixture.nativeElement.querySelector('.catalogue-filters-reset')).toBeNull();
  });

  it('should show the "Réinitialiser" button once the genre filter is active', () => {
    createComponent();

    component.filtersForm.controls.genreCode.setValue('FICTION');
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.catalogue-filters-reset')).not.toBeNull();
  });

  it('should show the "Réinitialiser" button once a filter is active', () => {
    createComponent();

    component.filtersForm.controls.language.setValue('FR');
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.catalogue-filters-reset')).not.toBeNull();
  });

  describe('synchronisation URL (q)', () => {
    it('should initialise the search field from the initial ?q= query parameter', () => {
      configure({ q: 'cosmologie' });
      createComponent();

      expect(component.filtersForm.controls.q.value).toBe('cosmologie');
      expect(lastParams()).toEqual({ page: 0, size: 20, q: 'cosmologie' });
    });

    it('should update the URL (merged, replaceUrl) once the debounced search settles', () => {
      createComponent();
      navigateSpy.mockClear();

      component.filtersForm.controls.q.setValue('Zola');
      vi.advanceTimersByTime(300);

      // L'état complet des 3 filtres est toujours envoyé (jamais un seul champ) : corrige un bug réel
      // démontré pendant VR27 (course entre plusieurs `navigate()` synchrones lors d'un `resetFilters()`,
      // invisible ici tant que le test ne combinait qu'un seul filtre à la fois).
      expect(navigateSpy).toHaveBeenCalledWith(
        [],
        expect.objectContaining({
          queryParams: { q: 'Zola', language: null, genreCode: null, view: null, size: null },
          queryParamsHandling: 'merge',
          replaceUrl: true,
        }),
      );
    });

    it('should remove the q query parameter once the search is cleared', () => {
      createComponent();
      component.filtersForm.controls.q.setValue('Zola');
      vi.advanceTimersByTime(300);
      navigateSpy.mockClear();

      component.clearSearch();
      vi.advanceTimersByTime(300);

      expect(navigateSpy).toHaveBeenCalledWith(
        [],
        expect.objectContaining({
          queryParams: { q: null, language: null, genreCode: null, view: null, size: null },
          queryParamsHandling: 'merge',
          replaceUrl: true,
        }),
      );
    });

    it('should apply an externally navigated ?q= change (e.g. a link from the Home page)', () => {
      createComponent();
      catalogueApiServiceMock.searchTitles.mockClear();

      queryParamMap$.next(convertToParamMap({ q: 'Einstein' }));
      vi.advanceTimersByTime(300);

      expect(component.filtersForm.controls.q.value).toBe('Einstein');
      expect(lastParams()).toEqual({ page: 0, size: 20, q: 'Einstein' });
    });

    it('should never re-trigger a search when the URL merely reflects our own write (no feedback loop)', () => {
      createComponent();
      component.filtersForm.controls.q.setValue('Zola');
      vi.advanceTimersByTime(300);
      catalogueApiServiceMock.searchTitles.mockClear();

      // The URL now genuinely carries the same value we just wrote — simulates the Router echoing our own navigate().
      queryParamMap$.next(convertToParamMap({ q: 'Zola' }));
      vi.advanceTimersByTime(300);

      expect(catalogueApiServiceMock.searchTitles).not.toHaveBeenCalled();
    });

    it('should allow searching the same term again after it was cleared in between (no distinctUntilChanged swallow, DEV-15)', () => {
      createComponent();
      component.filtersForm.controls.q.setValue('Zola');
      vi.advanceTimersByTime(300);
      component.clearSearch();
      vi.advanceTimersByTime(300);
      catalogueApiServiceMock.searchTitles.mockClear();

      component.filtersForm.controls.q.setValue('Zola');
      vi.advanceTimersByTime(300);

      expect(lastParams()).toEqual({ page: 0, size: 20, q: 'Zola' });
    });
  });

  describe('synchronisation URL (language, genreCode)', () => {
    it('should initialise language and genreCode from the initial query parameters', () => {
      configure({ language: 'FR', genreCode: 'FICTION' });
      createComponent();

      expect(component.filtersForm.controls.language.value).toBe('FR');
      expect(component.filtersForm.controls.genreCode.value).toBe('FICTION');
      expect(lastParams()).toEqual({ page: 0, size: 20, language: 'FR', genreCode: 'FICTION' });
    });

    it('should ignore an unknown initial ?language= value rather than transmitting it as-is', () => {
      configure({ language: 'BOGUS' });
      createComponent();

      expect(component.filtersForm.controls.language.value).toBeNull();
      expect(lastParams()).toEqual({ page: 0, size: 20 });
    });

    it('should update the URL (merged, replaceUrl) when the language filter changes', () => {
      createComponent();
      navigateSpy.mockClear();

      component.filtersForm.controls.language.setValue('EN');

      expect(navigateSpy).toHaveBeenCalledWith(
        [],
        expect.objectContaining({
          queryParams: { q: null, language: 'EN', genreCode: null, view: null, size: null },
          queryParamsHandling: 'merge',
          replaceUrl: true,
        }),
      );
    });

    it('should update the URL (merged, replaceUrl) when the genre filter changes', () => {
      createComponent();
      navigateSpy.mockClear();

      component.filtersForm.controls.genreCode.setValue('FANTASY');

      expect(navigateSpy).toHaveBeenCalledWith(
        [],
        expect.objectContaining({
          queryParams: { q: null, language: null, genreCode: 'FANTASY', view: null, size: null },
          queryParamsHandling: 'merge',
          replaceUrl: true,
        }),
      );
    });

    it('should remove the genreCode query parameter once reset to "Tous les genres"', () => {
      createComponent();
      component.filtersForm.controls.genreCode.setValue('FICTION');
      navigateSpy.mockClear();

      component.filtersForm.controls.genreCode.setValue(null);

      expect(navigateSpy).toHaveBeenCalledWith(
        [],
        expect.objectContaining({
          queryParams: { q: null, language: null, genreCode: null, view: null, size: null },
          queryParamsHandling: 'merge',
          replaceUrl: true,
        }),
      );
    });

    it('should apply an externally navigated ?genreCode= change without re-triggering on the echo', () => {
      createComponent();
      catalogueApiServiceMock.searchTitles.mockClear();

      queryParamMap$.next(convertToParamMap({ genreCode: 'FANTASY' }));

      expect(component.filtersForm.controls.genreCode.value).toBe('FANTASY');
      expect(lastParams()).toEqual({ page: 0, size: 20, genreCode: 'FANTASY' });

      catalogueApiServiceMock.searchTitles.mockClear();
      // The URL now genuinely carries the same value we just wrote — simulates the Router echoing our own navigate().
      queryParamMap$.next(convertToParamMap({ genreCode: 'FANTASY' }));

      expect(catalogueApiServiceMock.searchTitles).not.toHaveBeenCalled();
    });
  });

  describe('view mode URL persistence (DEV-CATALOGUE-VIEW-2)', () => {
    it('should default to grid when ?view= is absent', () => {
      createComponent();

      expect(component.viewMode()).toBe('grid');
    });

    it('should initialise the view from ?view=list', () => {
      configure({ view: 'list' });
      createComponent();

      expect(component.viewMode()).toBe('list');
      expect(fixture.nativeElement.querySelector('.catalogue-v3-list')).not.toBeNull();
    });

    it('should ignore an unknown ?view= value and fall back to grid', () => {
      configure({ view: 'bogus' });
      createComponent();

      expect(component.viewMode()).toBe('grid');
    });

    it('should write ?view=list to the URL (merged, replaceUrl) when switching to list, without a default view=grid ever appearing', () => {
      createComponent();
      navigateSpy.mockClear();

      component.setViewMode('list');

      expect(navigateSpy).toHaveBeenCalledWith(
        [],
        expect.objectContaining({
          queryParams: { q: null, language: null, genreCode: null, view: 'list', size: null },
          queryParamsHandling: 'merge',
          replaceUrl: true,
        }),
      );
    });

    it('should remove ?view= from the URL when switching back to grid (default omitted)', () => {
      createComponent();
      component.setViewMode('list');
      navigateSpy.mockClear();

      component.setViewMode('grid');

      expect(navigateSpy).toHaveBeenCalledWith(
        [],
        expect.objectContaining({
          queryParams: { q: null, language: null, genreCode: null, view: null, size: null },
          queryParamsHandling: 'merge',
          replaceUrl: true,
        }),
      );
    });

    it('should apply an externally navigated ?view= change (browser back/forward)', () => {
      createComponent();
      expect(component.viewMode()).toBe('grid');

      queryParamMap$.next(convertToParamMap({ view: 'list' }));

      expect(component.viewMode()).toBe('list');
    });

    it('should never re-trigger a search when ?view= merely echoes our own write', () => {
      createComponent();
      component.setViewMode('list');
      catalogueApiServiceMock.searchTitles.mockClear();

      queryParamMap$.next(convertToParamMap({ view: 'list' }));

      expect(catalogueApiServiceMock.searchTitles).not.toHaveBeenCalled();
    });
  });

  describe('page size (DEV-CATALOGUE-VIEW-2)', () => {
    it('should default to 20 per page', () => {
      createComponent();

      expect(component.pageSizeControl.value).toBe(20);
      expect(lastParams()).toEqual({ page: 0, size: 20 });
    });

    it('should initialise from ?size=40', () => {
      configure({ size: '40' });
      createComponent();

      expect(component.pageSizeControl.value).toBe(40);
      expect(lastParams()).toEqual({ page: 0, size: 40 });
    });

    it('should initialise from ?size=60', () => {
      configure({ size: '60' });
      createComponent();

      expect(component.pageSizeControl.value).toBe(60);
      expect(lastParams()).toEqual({ page: 0, size: 60 });
    });

    it('should ignore an invalid ?size= value not among 20/40/60 and fall back to 20', () => {
      configure({ size: '37' });
      createComponent();

      expect(component.pageSizeControl.value).toBe(20);
      expect(lastParams()).toEqual({ page: 0, size: 20 });
    });

    it('should ignore a non-numeric ?size= value and fall back to 20', () => {
      configure({ size: 'abc' });
      createComponent();

      expect(component.pageSizeControl.value).toBe(20);
      expect(lastParams()).toEqual({ page: 0, size: 20 });
    });

    it('should never transmit an arbitrary size not authorised by the UI control', () => {
      configure({ size: '1000' });
      createComponent();

      expect(component.pageSizeControl.value).toBe(20);
    });

    it('should reload page 0 with the new size when pageSizeControl changes', () => {
      createComponent();
      component.onPageChange({ first: 40, rows: 20 });
      catalogueApiServiceMock.searchTitles.mockClear();

      component.pageSizeControl.setValue(40);

      expect(lastParams()).toEqual({ page: 0, size: 40 });
    });

    it('should write ?size= to the URL (merged, replaceUrl) for a non-default size, and omit it for the default', () => {
      createComponent();
      navigateSpy.mockClear();

      component.pageSizeControl.setValue(60);

      expect(navigateSpy).toHaveBeenCalledWith(
        [],
        expect.objectContaining({
          queryParams: { q: null, language: null, genreCode: null, view: null, size: 60 },
          queryParamsHandling: 'merge',
          replaceUrl: true,
        }),
      );

      navigateSpy.mockClear();
      component.pageSizeControl.setValue(20);

      expect(navigateSpy).toHaveBeenCalledWith(
        [],
        expect.objectContaining({
          queryParams: { q: null, language: null, genreCode: null, view: null, size: null },
        }),
      );
    });

    it('should apply an externally navigated ?size= change (browser back/forward) and reload page 0', () => {
      createComponent();
      catalogueApiServiceMock.searchTitles.mockClear();

      queryParamMap$.next(convertToParamMap({ size: '40' }));

      expect(component.pageSizeControl.value).toBe(40);
      expect(lastParams()).toEqual({ page: 0, size: 40 });
    });

    it('should never re-trigger a search when ?size= merely echoes our own write', () => {
      createComponent();
      component.pageSizeControl.setValue(40);
      catalogueApiServiceMock.searchTitles.mockClear();

      queryParamMap$.next(convertToParamMap({ size: '40' }));

      expect(catalogueApiServiceMock.searchTitles).not.toHaveBeenCalled();
    });

    it('should keep the paginator rows in sync with the selected page size', () => {
      createComponent();

      component.pageSizeControl.setValue(60);

      expect(component.currentRows()).toBe(60);
      expect(component.currentFirst()).toBe(0);
    });
  });

  describe('combining q + language + genreCode + view + size, reset, and direct URL loads (DEV-CATALOGUE-VIEW-2)', () => {
    it('should combine all 5 state fields in a single request', () => {
      configure({ q: 'Zola', language: 'FR', genreCode: 'FICTION', view: 'list', size: '40' });
      createComponent();

      expect(component.viewMode()).toBe('list');
      expect(lastParams()).toEqual({
        page: 0,
        size: 40,
        q: 'Zola',
        language: 'FR',
        genreCode: 'FICTION',
      });
    });

    it('should load a direct URL combining view and size correctly (e.g. /catalogue?view=list&size=60)', () => {
      configure({ view: 'list', size: '60' });
      createComponent();

      expect(component.viewMode()).toBe('list');
      expect(component.pageSizeControl.value).toBe(60);
      expect(lastParams()).toEqual({ page: 0, size: 60 });
      expect(catalogueApiServiceMock.searchTitles).toHaveBeenCalledTimes(1);
    });

    it('resetFilters() should clear q/language/genreCode but preserve view and size', () => {
      configure({ view: 'list', size: '40' });
      createComponent();
      component.filtersForm.controls.q.setValue('Zola');
      component.filtersForm.controls.language.setValue('FR');
      component.filtersForm.controls.genreCode.setValue('FICTION');
      vi.advanceTimersByTime(300);
      navigateSpy.mockClear();

      component.resetFilters();
      vi.advanceTimersByTime(300);

      expect(component.viewMode()).toBe('list');
      expect(component.pageSizeControl.value).toBe(40);

      const lastCall = navigateSpy.mock.calls.at(-1);
      expect(lastCall?.[1]).toEqual(
        expect.objectContaining({
          queryParams: { q: null, language: null, genreCode: null, view: 'list', size: 40 },
        }),
      );
    });
  });
});
