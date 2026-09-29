import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

import { articleThumbnailAlt, articleThumbnailSrc } from '../../../articles/article-thumbnail.util';
import { ArticleSummaryResponse } from '../../../articles/models/article-summary-response';
import { ArticleApiService } from '../../../articles/services/article-api.service';
import { AuthService } from '../../../auth/services/auth.service';
import { TitleResponse } from '../../../catalogue/models/title-response';
import { CatalogueApiService } from '../../../catalogue/services/catalogue-api.service';
import { AppError } from '../../../core/errors/api-error';
import { toAppError } from '../../../core/errors/api-error.util';
import { EmptyState } from '../../ui/empty-state/empty-state';
import { ErrorState } from '../../ui/error-state/error-state';
import { LoadingState } from '../../ui/loading-state/loading-state';

const HOME_ARTICLES_LIMIT = 3;
const HOME_TITLES_LIMIT = 6;

interface QuickAccessItem {
  readonly iconPath: string;
  readonly label: string;
  readonly description: string;
  readonly routerLink: string;
}


@Component({
  selector: 'app-home',
  imports: [RouterLink, DatePipe, LoadingState, EmptyState, ErrorState],
  templateUrl: './home.html',
  styleUrl: './home.scss',
})
export class Home {
  private readonly articleApiService = inject(ArticleApiService);
  private readonly catalogueApiService = inject(CatalogueApiService);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  readonly articles = signal<ArticleSummaryResponse[]>([]);
  readonly articlesLoading = signal(true);
  readonly articlesError = signal<AppError | null>(null);

  readonly titles = signal<TitleResponse[]>([]);
  readonly titlesTotalCount = signal<number | null>(null);
  readonly titlesLoading = signal(true);
  readonly titlesError = signal<AppError | null>(null);

  
  accountQuickAccess(): QuickAccessItem {
    if (!this.authService.authenticated()) {
      return {
        iconPath: '/assets/icons/editorial/account.svg',
        // "Se connecter" (jamais "Connexion") : l'en-tête public
        // (`AccountMenu`) porte déjà un lien nommé exactement "Connexion" —
        // un second lien dont le nom accessible contiendrait le même mot
        // serait ambigu au clavier/lecteur d'écran (et rendait déjà
        // `getByRole('link', { name: 'Connexion' })` strict-mode ambigu
        // dans `auth-rbac.spec.ts`, cf. rapport DESIGN-V2-B1).
        label: 'Se connecter',
        description: 'Accédez à votre espace personnel PRIMATIS.',
        routerLink: '/login',
      };
    }
    if (this.authService.hasRole('ROLE_MEMBER')) {
      return {
        iconPath: '/assets/icons/editorial/account.svg',
        label: 'Mon espace',
        description: 'Suivez vos prêts, réservations et amendes.',
        routerLink: '/member/profile',
      };
    }
    return {
      iconPath: '/assets/icons/editorial/account.svg',
      label: 'Espace personnel',
      description: 'Accédez à vos outils de gestion PRIMATIS.',
      routerLink: '/staff/users',
    };
  }

  searchCatalogue(event: Event, rawQuery: string): void {
    event.preventDefault();
    const query = rawQuery.trim();
    void this.router.navigate(['/catalogue'], query ? { queryParams: { q: query } } : undefined);
  }

  /**
   * Vignette « À la une » (HOME-ARTICLES-MEDIA) : même logique que
   * `ArticleListPage` (`/articles`), mutualisée via
   * `article-thumbnail.util.ts` — jamais un mapping par index/position,
   * pour que la Home affiche exactement la même illustration que
   * `/articles` pour un même Article.
   */
  cardImageSrc(article: ArticleSummaryResponse): string {
    return articleThumbnailSrc(article, '960');
  }

  cardImageAlt(article: ArticleSummaryResponse): string {
    return articleThumbnailAlt(article);
  }

  constructor() {
    this.loadArticles();
    this.loadTitles();
  }

  retryArticles(): void {
    this.loadArticles();
  }

  retryTitles(): void {
    this.loadTitles();
  }

  private loadArticles(): void {
    this.articlesLoading.set(true);
    this.articlesError.set(null);

    this.articleApiService.listPublishedArticles(0, HOME_ARTICLES_LIMIT).subscribe({
      next: (response) => {
        this.articles.set(response.content);
        this.articlesLoading.set(false);
      },
      error: (err: unknown) => {
        this.articlesLoading.set(false);
        this.articlesError.set(toAppError(err));
      },
    });
  }

  private loadTitles(): void {
    this.titlesLoading.set(true);
    this.titlesError.set(null);

    this.catalogueApiService.searchTitles({ page: 0, size: HOME_TITLES_LIMIT }).subscribe({
      next: (response) => {
        this.titles.set(response.content);
        this.titlesTotalCount.set(response.totalElements);
        this.titlesLoading.set(false);
      },
      error: (err: unknown) => {
        this.titlesLoading.set(false);
        this.titlesError.set(toAppError(err));
      },
    });
  }
}
