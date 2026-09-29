import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { RouterLink } from '@angular/router';
import { PaginatorModule } from 'primeng/paginator';
import { PaginatorState } from 'primeng/types/paginator';

import { AppError } from '../../../core/errors/api-error';
import { toAppError } from '../../../core/errors/api-error.util';
import { EmptyState } from '../../../shared/ui/empty-state/empty-state';
import { ErrorState } from '../../../shared/ui/error-state/error-state';
import { LoadingState } from '../../../shared/ui/loading-state/loading-state';
import {
  articleThumbnailAlt,
  articleThumbnailFallback,
  articleThumbnailSrc,
  ArticleThumbnailSize,
} from '../../article-thumbnail.util';
import { ArticleSummaryResponse } from '../../models/article-summary-response';
import { ArticleApiService } from '../../services/article-api.service';

const DEFAULT_PAGE_SIZE = 20;


@Component({
  selector: 'app-article-list-page',
  imports: [RouterLink, DatePipe, PaginatorModule, LoadingState, EmptyState, ErrorState],
  templateUrl: './article-list-page.html',
  styleUrl: './article-list-page.scss',
})
export class ArticleListPage {
  private readonly articleApiService = inject(ArticleApiService);
  private readonly titleService = inject(Title);

  readonly rows = signal<ArticleSummaryResponse[]>([]);
  readonly totalRecords = signal(0);
  // Initialisé à true : le premier chargement est déclenché explicitement
  // ci-dessous (constructeur), même précédent que CataloguePage/StaffUsersPage.
  readonly loading = signal(true);
  readonly error = signal<AppError | null>(null);

  private lastPage = 0;
  private lastSize = DEFAULT_PAGE_SIZE;

  // `computed` plutôt qu'un getter : réévalué automatiquement quand `rows`
  // change, sans dépendre d'un cycle de détection déclenché ailleurs.
  private readonly onFirstPage = signal(true);

  readonly featured = computed<ArticleSummaryResponse | null>(() => {
    const list = this.rows();
    return this.onFirstPage() && list.length > 0 ? list[0] : null;
  });

  readonly gridRows = computed<ArticleSummaryResponse[]>(() => {
    const list = this.rows();
    return this.onFirstPage() ? list.slice(1) : list;
  });

  readonly latestArticles = computed<ArticleSummaryResponse[]>(() =>
    this.onFirstPage() ? this.rows().slice(1, 5) : [],
  );

  constructor() {
    this.titleService.setTitle('Articles — PRIMATIS');
    this.load(0, DEFAULT_PAGE_SIZE);
  }

  onPageChange(event: PaginatorState): void {
    const rows = event.rows ?? DEFAULT_PAGE_SIZE;
    const first = event.first ?? 0;
    this.load(Math.floor(first / rows), rows);
  }

  retry(): void {
    this.load(this.lastPage, this.lastSize);
  }

  currentFirst(): number {
    return this.lastPage * this.lastSize;
  }

  currentRows(): number {
    return this.lastSize;
  }

  authorName(author: ArticleSummaryResponse['author']): string {
    return `${author.firstName} ${author.lastName}`;
  }

  
  articleVisual(article: ArticleSummaryResponse, size: ArticleThumbnailSize): string {
    return articleThumbnailFallback(article, size);
  }

  cardImageSrc(article: ArticleSummaryResponse, size: ArticleThumbnailSize): string {
    return articleThumbnailSrc(article, size);
  }

  cardImageAlt(article: ArticleSummaryResponse): string {
    return articleThumbnailAlt(article);
  }

  private load(page: number, size: number): void {
    this.lastPage = page;
    this.lastSize = size;
    this.loading.set(true);
    this.error.set(null);

    this.articleApiService.listPublishedArticles(page, size).subscribe({
      next: (response) => {
        this.rows.set(response.content);
        this.totalRecords.set(response.totalElements);
        this.onFirstPage.set(page === 0);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.error.set(toAppError(err));
      },
    });
  }
}
