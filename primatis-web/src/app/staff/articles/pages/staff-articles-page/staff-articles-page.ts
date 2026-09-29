import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { Title } from '@angular/platform-browser';
import { RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { DatePickerModule } from 'primeng/datepicker';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TableLazyLoadEvent, TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { debounceTime, filter, map, merge, Subscription } from 'rxjs';

import { AppError } from '../../../../core/errors/api-error';
import { toAppError } from '../../../../core/errors/api-error.util';
import { formatUtcDateTime } from '../../../../shared/date/format-utc-date-time';
import { dateToIsoDate } from '../../../../shared/date/iso-date';
import { orderedDateRange } from '../../../../shared/date/ordered-date-range';
import { EmptyState } from '../../../../shared/ui/empty-state/empty-state';
import { ErrorState } from '../../../../shared/ui/error-state/error-state';
import { LoadingState } from '../../../../shared/ui/loading-state/loading-state';
import {
  articleStatusSeverity as sharedArticleStatusSeverity,
  StatusTagSeverity,
} from '../../../../shared/status/status-severity';
import { ArticleStatus } from '../../../../articles/models/article-status';
import { StaffArticleSearchFilters } from '../../../../articles/models/staff-article-search-filters';
import { StaffArticleSummaryResponse } from '../../../../articles/models/staff-article-summary-response';
import { StaffArticleApiService } from '../../../../articles/services/staff-article-api.service';

const DEFAULT_PAGE_SIZE = 20;
const SEARCH_DEBOUNCE_MS = 300;

interface ArticleStatusFilterOption {
  label: string;
  value: ArticleStatus | null;
}

// Mêmes libellés que les tags de statut du tableau (articleStatusLabel) ;
// valeurs = enum technique ArticleStatus du backend.
const ARTICLE_STATUS_FILTER_OPTIONS: ArticleStatusFilterOption[] = [
  { label: 'Tous les statuts', value: null },
  { label: 'Brouillon', value: 'DRAFT' },
  { label: 'Publié', value: 'PUBLISHED' },
  { label: 'Archivé', value: 'ARCHIVED' },
];


@Component({
  selector: 'app-staff-articles-page',
  imports: [
    RouterLink,
    ReactiveFormsModule,
    InputTextModule,
    DatePickerModule,
    SelectModule,
    ButtonModule,
    TableModule,
    TagModule,
    LoadingState,
    EmptyState,
    ErrorState,
  ],
  templateUrl: './staff-articles-page.html',
  styleUrl: './staff-articles-page.scss',
})
export class StaffArticlesPage {
  private readonly staffArticleApiService = inject(StaffArticleApiService);
  private readonly titleService = inject(Title);
  private readonly formBuilder = inject(NonNullableFormBuilder);

  readonly articleStatusOptions = ARTICLE_STATUS_FILTER_OPTIONS;

  readonly filtersForm = this.formBuilder.group(
    {
      q: this.formBuilder.control(''),
      articleStatus: this.formBuilder.control<ArticleStatus | null>(null),
      modifiedDateFrom: this.formBuilder.control<Date | null>(null),
      modifiedDateTo: this.formBuilder.control<Date | null>(null),
      publishedDateFrom: this.formBuilder.control<Date | null>(null),
      publishedDateTo: this.formBuilder.control<Date | null>(null),
    },
    {
      validators: [
        orderedDateRange('modifiedDateFrom', 'modifiedDateTo', 'modifiedDateRange'),
        orderedDateRange('publishedDateFrom', 'publishedDateTo', 'publishedDateRange'),
      ],
    },
  );

  readonly rows = signal<StaffArticleSummaryResponse[]>([]);
  readonly totalRecords = signal(0);
  readonly loading = signal(true);
  readonly error = signal<AppError | null>(null);

  // Premier enregistrement affiché, lié au paginateur (retour page 0 après
  // un changement de critère) ; critères réellement appliqués à la dernière
  // requête, pour l'état vide.
  readonly first = signal(0);
  readonly filtersApplied = signal(false);

  private lastPage = 0;
  private lastSize = DEFAULT_PAGE_SIZE;
  // Dernier `q` (trimé) réellement envoyé (distinct-until-changed correct après « Effacer »).
  private appliedQuery = '';
  // Critères effectifs de la dernière requête (JSON) : `p-datepicker` réémet
  // `null` à chaque frappe partielle d'une date ; sans cette comparaison,
  // chaque caractère relancerait une requête identique.
  private appliedCriteriaKey = '';
  private request: Subscription | null = null;

  constructor() {
    this.titleService.setTitle('Articles — PRIMATIS');

    const controls = this.filtersForm.controls;
    controls.q.valueChanges
      .pipe(
        map((value) => value.trim()),
        debounceTime(SEARCH_DEBOUNCE_MS),
        filter((value) => value !== this.appliedQuery),
        takeUntilDestroyed(),
      )
      .subscribe(() => this.reloadFromCriteria());

    merge(
      controls.articleStatus.valueChanges,
      controls.modifiedDateFrom.valueChanges,
      controls.modifiedDateTo.valueChanges,
      controls.publishedDateFrom.valueChanges,
      controls.publishedDateTo.valueChanges,
    )
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.reloadFromCriteria());

    this.load(0, DEFAULT_PAGE_SIZE);
  }

  hasActiveFilters(): boolean {
    const raw = this.filtersForm.getRawValue();
    return (
      raw.q.trim().length > 0 ||
      raw.articleStatus !== null ||
      !!raw.modifiedDateFrom ||
      !!raw.modifiedDateTo ||
      !!raw.publishedDateFrom ||
      !!raw.publishedDateTo
    );
  }

  /** Réinitialise tous les critères et recharge une seule fois, page 0. */
  clearFilters(): void {
    this.filtersForm.reset(
      {
        q: '',
        articleStatus: null,
        modifiedDateFrom: null,
        modifiedDateTo: null,
        publishedDateFrom: null,
        publishedDateTo: null,
      },
      { emitEvent: false },
    );
    this.load(0, this.lastSize);
  }

  /** Instant ISO → `jj/mm/aaaa hh:mm UTC` ; `null` → `—`. Jamais la chaîne ISO brute. */
  formatDateTime(value: string | null): string {
    return formatUtcDateTime(value);
  }

  onLazyLoad(event: TableLazyLoadEvent): void {
    const rows = event.rows ?? DEFAULT_PAGE_SIZE;
    const first = event.first ?? 0;
    this.load(Math.floor(first / rows), rows);
  }

  retry(): void {
    this.load(this.lastPage, this.lastSize);
  }

  authorName(author: StaffArticleSummaryResponse['author']): string {
    return `${author.firstName} ${author.lastName}`;
  }

  articleStatusSeverity(status: ArticleStatus): StatusTagSeverity {
    return sharedArticleStatusSeverity(status);
  }

  articleStatusLabel(status: ArticleStatus): string {
    switch (status) {
      case 'DRAFT':
        return 'Brouillon';
      case 'PUBLISHED':
        return 'Publié';
      case 'ARCHIVED':
        return 'Archivé';
    }
  }

  /**
   * Changement d'un critère : retour page 0. Un intervalle incohérent
   * n'appelle pas le backend (message sous le groupe de dates) ; la liste
   * précédente reste affichée jusqu'à correction.
   */
  private reloadFromCriteria(): void {
    // `valueChanges` d'un contrôle enfant émet avant la revalidation du
    // groupe parent : sans ceci, `invalid` reflèterait l'état précédent.
    this.filtersForm.updateValueAndValidity({ emitEvent: false });
    if (
      this.filtersForm.invalid ||
      JSON.stringify(this.currentFilters()) === this.appliedCriteriaKey
    ) {
      return;
    }
    this.load(0, this.lastSize);
  }

  private currentFilters(): StaffArticleSearchFilters {
    const raw = this.filtersForm.getRawValue();
    const filters: StaffArticleSearchFilters = {};
    const q = raw.q.trim();
    if (q) filters.q = q;
    if (raw.articleStatus) filters.articleStatus = raw.articleStatus;
    const modifiedDateFrom = dateToIsoDate(raw.modifiedDateFrom);
    if (modifiedDateFrom) filters.modifiedDateFrom = modifiedDateFrom;
    const modifiedDateTo = dateToIsoDate(raw.modifiedDateTo);
    if (modifiedDateTo) filters.modifiedDateTo = modifiedDateTo;
    const publishedDateFrom = dateToIsoDate(raw.publishedDateFrom);
    if (publishedDateFrom) filters.publishedDateFrom = publishedDateFrom;
    const publishedDateTo = dateToIsoDate(raw.publishedDateTo);
    if (publishedDateTo) filters.publishedDateTo = publishedDateTo;
    return filters;
  }

  private load(page: number, size: number): void {
    this.lastPage = page;
    this.lastSize = size;
    this.first.set(page * size);
    this.loading.set(true);
    this.error.set(null);

    const filters = this.currentFilters();
    this.appliedQuery = filters.q ?? '';
    this.appliedCriteriaKey = JSON.stringify(filters);
    this.filtersApplied.set(Object.keys(filters).length > 0);

    // Une réponse tardive d'une requête devenue obsolète ne doit jamais
    // écraser le résultat de la requête la plus récente.
    this.request?.unsubscribe();
    this.request = this.staffArticleApiService.listStaffArticles(page, size, filters).subscribe({
      next: (response) => {
        this.rows.set(response.content);
        this.totalRecords.set(response.totalElements);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.error.set(toAppError(err));
      },
    });
  }
}
