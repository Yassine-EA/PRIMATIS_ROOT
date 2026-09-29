import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Subject, of, throwError } from 'rxjs';
import { afterEach, vi } from 'vitest';

import { isoDateToDate } from '../../../../shared/date/iso-date';
import { PageResponse } from '../../../../core/models/page-response';
import { StaffArticleSummaryResponse } from '../../../../articles/models/staff-article-summary-response';
import { StaffArticleApiService } from '../../../../articles/services/staff-article-api.service';
import { StaffArticlesPage } from './staff-articles-page';

function buildSummary(overrides: Partial<StaffArticleSummaryResponse> = {}): StaffArticleSummaryResponse {
  return {
    id: 1,
    title: 'Nouvelle acquisition',
    summary: 'Résumé',
    slug: 'nouvelle-acquisition',
    articleStatus: 'DRAFT',
    author: { id: 10, firstName: 'Prénom', lastName: 'Nom' },
    publishedAt: null,
    updatedAt: '2026-08-01T08:00:00Z',
    ...overrides,
  };
}

function buildPage(content: StaffArticleSummaryResponse[], totalElements = content.length): PageResponse<StaffArticleSummaryResponse> {
  return { content, page: 0, size: 20, totalElements, totalPages: Math.max(1, Math.ceil(totalElements / 20)) };
}

function apiHttpError(message: string): HttpErrorResponse {
  return new HttpErrorResponse({
    status: 500,
    error: {
      timestamp: new Date().toISOString(),
      status: 500,
      error: 'Internal Server Error',
      code: 'INTERNAL_ERROR',
      message,
      path: '/api/v1/staff/articles',
      fieldErrors: [],
    },
  });
}

describe('StaffArticlesPage', () => {
  let fixture: ComponentFixture<StaffArticlesPage>;
  let component: StaffArticlesPage;
  let staffArticleApiServiceMock: { listStaffArticles: ReturnType<typeof vi.fn> };

  function configure(): void {
    staffArticleApiServiceMock = { listStaffArticles: vi.fn().mockReturnValue(of(buildPage([buildSummary()]))) };

    TestBed.configureTestingModule({
      imports: [StaffArticlesPage],
      providers: [provideRouter([]), { provide: StaffArticleApiService, useValue: staffArticleApiServiceMock }],
    });
  }

  function createComponent(): void {
    fixture = TestBed.createComponent(StaffArticlesPage);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  beforeEach(() => configure());

  it('should load Articles on initial construction with page=0 and the default size', () => {
    createComponent();

    expect(staffArticleApiServiceMock.listStaffArticles).toHaveBeenCalledWith(0, 20, {});
    expect(component.rows()).toEqual([buildSummary()]);
    expect(component.loading()).toBe(false);
  });

  it('should display all three ArticleStatus values, not only PUBLISHED', () => {
    staffArticleApiServiceMock.listStaffArticles.mockReturnValue(
      of(
        buildPage([
          buildSummary({ id: 1, articleStatus: 'DRAFT', publishedAt: null }),
          buildSummary({ id: 2, articleStatus: 'PUBLISHED', publishedAt: '2026-08-01T08:00:00Z' }),
          buildSummary({ id: 3, articleStatus: 'ARCHIVED', publishedAt: '2026-08-01T08:00:00Z' }),
        ]),
      ),
    );
    createComponent();

    expect(fixture.nativeElement.textContent).toContain('Brouillon');
    expect(fixture.nativeElement.textContent).toContain('Publié');
    expect(fixture.nativeElement.textContent).toContain('Archivé');
  });

  it('should set the document title (DEV-15.8)', () => {
    createComponent();

    expect(document.title).toBe('Articles — PRIMATIS');
  });

  it('should never render the article content in the list', () => {
    createComponent();

    expect(component.rows()[0]).not.toHaveProperty('content');
  });

  it('should render title/author/updatedAt/publishedAt columns', () => {
    createComponent();

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Nouvelle acquisition');
    expect(text).toContain('Prénom Nom');
    expect(text).toContain('01/08/2026 08:00 UTC');
  });

  it('should link each row to its staff detail page by id', () => {
    createComponent();

    const link: HTMLAnchorElement = fixture.nativeElement.querySelector('a[href="/staff/articles/1"]');
    expect(link).not.toBeNull();
  });

  it('should link to the create page', () => {
    createComponent();

    expect(fixture.nativeElement.querySelector('a[href="/staff/articles/new"]')).not.toBeNull();
  });

  it('should link to the Tag management page', () => {
    createComponent();

    expect(fixture.nativeElement.querySelector('a[href="/staff/articles/tags"]')).not.toBeNull();
  });

  it('should convert a lazy-load event to a 0-based page/size call', () => {
    createComponent();
    staffArticleApiServiceMock.listStaffArticles.mockClear();

    component.onLazyLoad({ first: 40, rows: 20 });

    expect(staffArticleApiServiceMock.listStaffArticles).toHaveBeenCalledWith(2, 20, {});
  });

  it('should show the empty state when no Article exists', () => {
    staffArticleApiServiceMock.listStaffArticles.mockReturnValue(of(buildPage([])));
    createComponent();

    expect(fixture.nativeElement.textContent).toContain('Aucun article');
  });

  it('should show the error state on load failure, with a retry that reloads', () => {
    staffArticleApiServiceMock.listStaffArticles.mockReturnValue(throwError(() => apiHttpError('Erreur serveur.')));
    createComponent();

    expect(component.error()?.message).toBe('Erreur serveur.');

    staffArticleApiServiceMock.listStaffArticles.mockReturnValue(of(buildPage([buildSummary()])));
    component.retry();

    expect(component.error()).toBeNull();
    expect(component.rows()).toEqual([buildSummary()]);
  });
  it('should render the VISUAL-RESET-19 editorial structure', () => {
    createComponent();

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Gestion des articles');
    expect(text).toContain('Registre éditorial');
    expect(text).toContain('Articles enregistrés');
    expect(text).toContain('1 article');
  });

  it('should expose responsive data labels and a dedicated consult action', () => {
    createComponent();

    const row = fixture.nativeElement.querySelector('.staff-articles-table tbody tr') as HTMLTableRowElement;
    expect(row.querySelector('td[data-label="Article"]')).not.toBeNull();
    expect(row.querySelector('td[data-label="Statut"]')).not.toBeNull();
    expect(row.querySelector('td[data-label="Auteur"]')).not.toBeNull();
    expect(row.querySelector('td[data-label="Modifié le"]')).not.toBeNull();
    expect(row.querySelector('td[data-label="Publié le"]')).not.toBeNull();

    const action = row.querySelector('a[href="/staff/articles/1"]') as HTMLAnchorElement;
    expect(action).not.toBeNull();
    expect(action.textContent).toContain('Consulter');
  });

  describe('date display', () => {
    it('should render Modifié le / Publié le in French UTC, never as raw ISO', () => {
      staffArticleApiServiceMock.listStaffArticles.mockReturnValue(
        of(
          buildPage([
            buildSummary({
              id: 1,
              articleStatus: 'PUBLISHED',
              updatedAt: '2026-09-19T18:27:55.918632Z',
              publishedAt: '2026-09-19T18:26:28.017317Z',
            }),
            buildSummary({ id: 2, articleStatus: 'DRAFT', updatedAt: '2026-09-19T18:27:55Z', publishedAt: null }),
          ]),
        ),
      );
      createComponent();

      const root = fixture.nativeElement as HTMLElement;
      const modified = Array.from(root.querySelectorAll('td[data-label="Modifié le"]')).map((c) => c.textContent?.trim());
      const published = Array.from(root.querySelectorAll('td[data-label="Publié le"]')).map((c) => c.textContent?.trim());
      expect(modified).toEqual(['19/09/2026 18:27 UTC', '19/09/2026 18:27 UTC']);
      expect(published).toEqual(['19/09/2026 18:26 UTC', '—']);
      expect(root.querySelector('.staff-articles-table')?.textContent).not.toMatch(/\d{4}-\d{2}-\d{2}T\d{2}:/);
    });
  });

  
  
  

  describe('search and filters', () => {
    function lastCall(): unknown[] {
      return staffArticleApiServiceMock.listStaffArticles.mock.calls.at(-1) as unknown[];
    }

    beforeEach(() => {
      vi.useFakeTimers();
      staffArticleApiServiceMock.listStaffArticles.mockReturnValue(of(buildPage([buildSummary()], 100)));
      createComponent();
      staffArticleApiServiceMock.listStaffArticles.mockClear();
    });

    afterEach(() => vi.useRealTimers());

    it('should render labelled controls with French labels and technical values', () => {
      const root = fixture.nativeElement as HTMLElement;

      expect(root.querySelector('label[for="staff-articles-search"]')?.textContent).toContain('Rechercher un article');
      expect((root.querySelector('#staff-articles-search') as HTMLInputElement).placeholder).toBe(
        'Rechercher par titre ou auteur…',
      );
      expect(root.querySelector('label[for="staff-articles-status"]')?.textContent).toContain('Statut');
      expect(Array.from(root.querySelectorAll('legend')).map((l) => l.textContent?.trim())).toEqual([
        'Modifié le',
        'Publié le',
      ]);
      expect(root.querySelector<HTMLInputElement>('input#staff-articles-modified-from')?.placeholder).toBe('jj/mm/aaaa');
      expect(root.querySelector<HTMLInputElement>('input#staff-articles-published-to')?.placeholder).toBe('jj/mm/aaaa');
      expect(component.articleStatusOptions.map((o) => o.label)).toEqual(['Tous les statuts', 'Brouillon', 'Publié', 'Archivé']);
      expect(component.articleStatusOptions.map((o) => o.value)).toEqual([null, 'DRAFT', 'PUBLISHED', 'ARCHIVED']);
    });

    it('should debounce q, trim it and reset to page 0', () => {
      component.onLazyLoad({ first: 40, rows: 20 });
      staffArticleApiServiceMock.listStaffArticles.mockClear();

      component.filtersForm.controls.q.setValue('  lemaître ');
      vi.advanceTimersByTime(299);
      expect(staffArticleApiServiceMock.listStaffArticles).not.toHaveBeenCalled();

      vi.advanceTimersByTime(1);
      expect(staffArticleApiServiceMock.listStaffArticles).toHaveBeenCalledTimes(1);
      expect(lastCall()).toEqual([0, 20, { q: 'lemaître' }]);
    });

    it('should send one request for a burst of keystrokes and none for an unchanged trimmed q', () => {
      component.filtersForm.controls.q.setValue('l');
      vi.advanceTimersByTime(100);
      component.filtersForm.controls.q.setValue('lem');
      vi.advanceTimersByTime(300);
      expect(staffArticleApiServiceMock.listStaffArticles).toHaveBeenCalledTimes(1);

      component.filtersForm.controls.q.setValue('lem  ');
      vi.advanceTimersByTime(300);
      expect(staffArticleApiServiceMock.listStaffArticles).toHaveBeenCalledTimes(1);
    });

    it('should send articleStatus immediately and reset to page 0', () => {
      component.onLazyLoad({ first: 40, rows: 20 });
      staffArticleApiServiceMock.listStaffArticles.mockClear();

      component.filtersForm.controls.articleStatus.setValue('DRAFT');

      expect(lastCall()).toEqual([0, 20, { articleStatus: 'DRAFT' }]);
      expect(component.first()).toBe(0);
    });

    it('should send the modified and published ranges as ISO days', () => {
      component.filtersForm.controls.modifiedDateFrom.setValue(isoDateToDate('2026-09-01'));
      component.filtersForm.controls.modifiedDateTo.setValue(isoDateToDate('2026-09-30'));
      component.filtersForm.controls.publishedDateFrom.setValue(isoDateToDate('2026-09-10'));
      component.filtersForm.controls.publishedDateTo.setValue(new Date(2026, 8, 20, 23, 59, 59));

      expect(lastCall()).toEqual([
        0,
        20,
        {
          modifiedDateFrom: '2026-09-01',
          modifiedDateTo: '2026-09-30',
          publishedDateFrom: '2026-09-10',
          publishedDateTo: '2026-09-20',
        },
      ]);
    });

    it('should accept open-ended and same-day ranges', () => {
      component.filtersForm.controls.publishedDateTo.setValue(isoDateToDate('2026-09-15'));
      expect(lastCall()).toEqual([0, 20, { publishedDateTo: '2026-09-15' }]);
      component.filtersForm.controls.publishedDateFrom.setValue(isoDateToDate('2026-09-15'));

      expect(lastCall()).toEqual([0, 20, { publishedDateFrom: '2026-09-15', publishedDateTo: '2026-09-15' }]);
    });

    it('should not reload when a datepicker re-emits an unchanged empty value (partial keystrokes)', () => {
      for (let i = 0; i < 5; i++) {
        component.filtersForm.controls.modifiedDateTo.setValue(null);
        component.filtersForm.controls.publishedDateFrom.setValue(null);
      }

      expect(staffArticleApiServiceMock.listStaffArticles).not.toHaveBeenCalled();
    });

    it('should reload once for a date typed after several empty re-emissions', () => {
      component.filtersForm.controls.modifiedDateTo.setValue(null);
      component.filtersForm.controls.modifiedDateTo.setValue(null);
      component.filtersForm.controls.modifiedDateTo.setValue(isoDateToDate('2026-09-10'));
      component.filtersForm.controls.modifiedDateTo.setValue(isoDateToDate('2026-09-10'));

      expect(staffArticleApiServiceMock.listStaffArticles).toHaveBeenCalledTimes(1);
    });

    it('should combine q, status and both ranges', () => {
      component.filtersForm.controls.articleStatus.setValue('PUBLISHED');
      component.filtersForm.controls.modifiedDateFrom.setValue(isoDateToDate('2026-08-01'));
      component.filtersForm.controls.publishedDateTo.setValue(isoDateToDate('2026-09-15'));
      component.filtersForm.controls.q.setValue('lemaître');
      vi.advanceTimersByTime(300);

      expect(lastCall()).toEqual([
        0,
        20,
        { q: 'lemaître', articleStatus: 'PUBLISHED', modifiedDateFrom: '2026-08-01', publishedDateTo: '2026-09-15' },
      ]);
    });

    it('should keep every criterion when the page changes', () => {
      component.filtersForm.controls.articleStatus.setValue('ARCHIVED');
      component.filtersForm.controls.modifiedDateFrom.setValue(isoDateToDate('2026-08-01'));
      staffArticleApiServiceMock.listStaffArticles.mockClear();

      component.onLazyLoad({ first: 20, rows: 20 });

      expect(lastCall()).toEqual([1, 20, { articleStatus: 'ARCHIVED', modifiedDateFrom: '2026-08-01' }]);
      expect(component.first()).toBe(20);
    });

    it('should not call the API and should show the range message for an inverted modified range', () => {
      component.filtersForm.controls.modifiedDateFrom.setValue(isoDateToDate('2026-09-10'));
      staffArticleApiServiceMock.listStaffArticles.mockClear();

      component.filtersForm.controls.modifiedDateTo.setValue(isoDateToDate('2026-09-01'));
      fixture.detectChanges();

      expect(staffArticleApiServiceMock.listStaffArticles).not.toHaveBeenCalled();
      const root = fixture.nativeElement as HTMLElement;
      expect(root.querySelector('#staff-articles-modified-range-error')?.textContent).toContain(
        'La date de début doit précéder la date de fin.',
      );
      expect(root.querySelector('#staff-articles-published-range-error')).toBeNull();
    });

    it('should not call the API and should show the range message for an inverted published range', () => {
      component.filtersForm.controls.publishedDateFrom.setValue(isoDateToDate('2026-09-10'));
      staffArticleApiServiceMock.listStaffArticles.mockClear();

      component.filtersForm.controls.publishedDateTo.setValue(isoDateToDate('2026-09-01'));
      fixture.detectChanges();

      expect(staffArticleApiServiceMock.listStaffArticles).not.toHaveBeenCalled();
      expect((fixture.nativeElement as HTMLElement).querySelector('#staff-articles-published-range-error')).not.toBeNull();
    });

    it('should search again once an inverted range is corrected', () => {
      component.filtersForm.controls.publishedDateFrom.setValue(isoDateToDate('2026-09-10'));
      component.filtersForm.controls.publishedDateTo.setValue(isoDateToDate('2026-09-01'));
      staffArticleApiServiceMock.listStaffArticles.mockClear();

      component.filtersForm.controls.publishedDateTo.setValue(isoDateToDate('2026-09-30'));

      expect(lastCall()).toEqual([0, 20, { publishedDateFrom: '2026-09-10', publishedDateTo: '2026-09-30' }]);
    });

    it('should show the clear button only when a criterion is active', () => {
      const root = fixture.nativeElement as HTMLElement;
      expect(root.querySelector('p-button[label="Effacer"]')).toBeNull();

      component.filtersForm.controls.articleStatus.setValue('DRAFT');
      fixture.detectChanges();

      expect(root.querySelector('p-button[label="Effacer"]')).not.toBeNull();
    });

    it('should clear every criterion with a single request on page 0', () => {
      component.filtersForm.controls.articleStatus.setValue('PUBLISHED');
      component.filtersForm.controls.modifiedDateFrom.setValue(isoDateToDate('2026-08-01'));
      component.filtersForm.controls.publishedDateTo.setValue(isoDateToDate('2026-09-15'));
      component.filtersForm.controls.q.setValue('lemaître');
      vi.advanceTimersByTime(300);
      component.onLazyLoad({ first: 20, rows: 20 });
      staffArticleApiServiceMock.listStaffArticles.mockClear();

      component.clearFilters();
      vi.advanceTimersByTime(1000);

      expect(staffArticleApiServiceMock.listStaffArticles).toHaveBeenCalledTimes(1);
      expect(lastCall()).toEqual([0, 20, {}]);
      expect(component.filtersForm.getRawValue()).toEqual({
        q: '',
        articleStatus: null,
        modifiedDateFrom: null,
        modifiedDateTo: null,
        publishedDateFrom: null,
        publishedDateTo: null,
      });
      expect(component.hasActiveFilters()).toBe(false);
    });

    it('should clear an inverted range without sending an invalid request', () => {
      component.filtersForm.controls.modifiedDateFrom.setValue(isoDateToDate('2026-09-10'));
      component.filtersForm.controls.modifiedDateTo.setValue(isoDateToDate('2026-09-01'));
      staffArticleApiServiceMock.listStaffArticles.mockClear();

      component.clearFilters();

      expect(lastCall()).toEqual([0, 20, {}]);
      expect(component.filtersForm.valid).toBe(true);
    });

    it('should search again for the same q after clearing', () => {
      component.filtersForm.controls.q.setValue('lemaître');
      vi.advanceTimersByTime(300);
      component.clearFilters();
      staffArticleApiServiceMock.listStaffArticles.mockClear();

      component.filtersForm.controls.q.setValue('lemaître');
      vi.advanceTimersByTime(300);

      expect(lastCall()).toEqual([0, 20, { q: 'lemaître' }]);
    });

    it('should show the search-specific empty state and keep the filters visible', () => {
      staffArticleApiServiceMock.listStaffArticles.mockReturnValue(of(buildPage([], 0)));

      component.filtersForm.controls.articleStatus.setValue('ARCHIVED');
      fixture.detectChanges();

      const root = fixture.nativeElement as HTMLElement;
      expect(root.querySelector('app-empty-state')?.textContent).toContain('Aucun article ne correspond à votre recherche.');
      expect(root.querySelector('#staff-articles-search')).not.toBeNull();
      expect(root.querySelector('p-button[label="Effacer"]')).not.toBeNull();
    });

    it('should ignore a stale response arriving after a newer request', () => {
      const first$ = new Subject<PageResponse<StaffArticleSummaryResponse>>();
      const second$ = new Subject<PageResponse<StaffArticleSummaryResponse>>();
      staffArticleApiServiceMock.listStaffArticles.mockReturnValueOnce(first$).mockReturnValueOnce(second$);

      component.filtersForm.controls.articleStatus.setValue('DRAFT');
      component.filtersForm.controls.articleStatus.setValue('PUBLISHED');
      second$.next(buildPage([buildSummary({ id: 2 })], 1));
      first$.next(buildPage([buildSummary({ id: 1 }), buildSummary({ id: 3 })], 2));

      expect(component.rows().map((a) => a.id)).toEqual([2]);
      expect(component.totalRecords()).toBe(1);
    });

    it('should keep the consult link, create link and tags link with filters active', () => {
      component.filtersForm.controls.articleStatus.setValue('DRAFT');
      fixture.detectChanges();

      const root = fixture.nativeElement as HTMLElement;
      expect(root.querySelector('a[href="/staff/articles/1"]')).not.toBeNull();
      expect(root.querySelector('a[href="/staff/articles/new"]')).not.toBeNull();
      expect(root.querySelector('a[href="/staff/articles/tags"]')).not.toBeNull();
    });
  });
});
