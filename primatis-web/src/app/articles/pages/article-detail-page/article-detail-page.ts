import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TagModule } from 'primeng/tag';

import { AppError } from '../../../core/errors/api-error';
import { toAppError } from '../../../core/errors/api-error.util';
import { ErrorState } from '../../../shared/ui/error-state/error-state';
import { LoadingState } from '../../../shared/ui/loading-state/loading-state';
import { ArticleContent } from '../../components/article-content/article-content';
import { ArticleResponse } from '../../models/article-response';
import { ArticleApiService } from '../../services/article-api.service';

const INVALID_SLUG_ERROR: AppError = { message: 'Slug d’article invalide.', fieldErrors: [] };


@Component({
  selector: 'app-article-detail-page',
  imports: [RouterLink, DatePipe, TagModule, LoadingState, ErrorState, ArticleContent],
  templateUrl: './article-detail-page.html',
  styleUrl: './article-detail-page.scss',
})
export class ArticleDetailPage {
  private readonly route = inject(ActivatedRoute);
  private readonly articleApiService = inject(ArticleApiService);
  private readonly titleService = inject(Title);

  readonly article = signal<ArticleResponse | null>(null);
  readonly loading = signal(false);
  readonly error = signal<AppError | null>(null);

  constructor() {
    this.route.paramMap.pipe(takeUntilDestroyed()).subscribe((params) => {
      const slug = params.get('slug');
      if (slug === null) {
        this.article.set(null);
        this.loading.set(false);
        this.error.set(INVALID_SLUG_ERROR);
        return;
      }
      this.loadArticle(slug);
    });
  }

  authorName(article: ArticleResponse): string {
    return `${article.author.firstName} ${article.author.lastName}`;
  }

  private loadArticle(slug: string): void {
    this.loading.set(true);
    this.error.set(null);
    // Toujours réinitialiser l'Article précédemment affiché : le composant
    // peut être réutilisé par le Router lors d'un changement de :slug (même
    // route, nouveau paramètre) — éviter d'afficher un contenu périmé
    // pendant le chargement du nouveau slug.
    this.article.set(null);

    this.articleApiService.getPublishedArticleBySlug(slug).subscribe({
      next: (value) => {
        this.article.set(value);
        this.loading.set(false);
        this.titleService.setTitle(`${value.title} — PRIMATIS`);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.error.set(toAppError(err));
      },
    });
  }
}
