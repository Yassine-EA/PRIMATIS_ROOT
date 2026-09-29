import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MessageService } from 'primeng/api';
import { TagModule } from 'primeng/tag';

import { AuthService } from '../../../auth/services/auth.service';
import { AppError } from '../../../core/errors/api-error';
import { toAppError } from '../../../core/errors/api-error.util';
import { CreateOwnReservationRequest } from '../../../reservations/models/create-own-reservation-request';
import { ReservationApiService } from '../../../reservations/services/reservation-api.service';
import { ErrorState } from '../../../shared/ui/error-state/error-state';
import { LoadingState } from '../../../shared/ui/loading-state/loading-state';
import { Language } from '../../models/language';
import { TitleDetailResponse } from '../../models/title-detail-response';
import { CatalogueApiService } from '../../services/catalogue-api.service';

const INVALID_TITLE_ID_ERROR: AppError = { message: 'Identifiant de titre invalide.', fieldErrors: [] };

// Même libellés français que `CataloguePage`/`staff/catalogue/language-options.ts` —
// dupliqué localement plutôt qu'importé depuis `staff/` (features distinctes,
// même précédent que la duplication déjà existante entre ces deux fichiers).
const LANGUAGE_LABELS: Record<Language, string> = {
  FR: 'Français',
  EN: 'Anglais',
  NL: 'Néerlandais',
  DE: 'Allemand',
  ES: 'Espagnol',
  IT: 'Italien',
  LA: 'Latin',
};

type ReservationState = 'idle' | 'pending' | 'success' | 'error';


@Component({
  selector: 'app-title-detail-page',
  imports: [RouterLink, TagModule, LoadingState, ErrorState],
  templateUrl: './title-detail-page.html',
  styleUrl: './title-detail-page.scss',
})
export class TitleDetailPage {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly catalogueApiService = inject(CatalogueApiService);
  private readonly reservationApiService = inject(ReservationApiService);
  private readonly authService = inject(AuthService);
  private readonly messageService = inject(MessageService);
  private readonly titleService = inject(Title);

  readonly title = signal<TitleDetailResponse | null>(null);
  readonly loading = signal(false);
  readonly error = signal<AppError | null>(null);

  readonly reservationState = signal<ReservationState>('idle');
  readonly reservationError = signal<string | null>(null);

  constructor() {
    this.route.paramMap.pipe(takeUntilDestroyed()).subscribe((params) => {
      const id = parseTitleId(params.get('id'));
      if (id === null) {
        this.title.set(null);
        this.loading.set(false);
        this.error.set(INVALID_TITLE_ID_ERROR);
        return;
      }
      this.loadTitle(id);
    });
  }

  authorNames(detail: TitleDetailResponse): string {
    return detail.authors.map((author) => author.fullName).join(', ');
  }

  languageLabel(language: Language): string {
    return LANGUAGE_LABELS[language] ?? language;
  }

  canReserve(): boolean {
    return this.authService.authenticated() && this.authService.hasRole('ROLE_MEMBER');
  }

  showLoginCta(): boolean {
    return !this.authService.authenticated();
  }

  loginReturnUrl(): string {
    return this.router.url;
  }

  reserve(titleId: number): void {
    if (this.reservationState() === 'pending' || this.reservationState() === 'success') {
      return;
    }
    this.reservationState.set('pending');
    this.reservationError.set(null);

    const request: CreateOwnReservationRequest = { titleId };
    this.reservationApiService.createOwnReservation(request).subscribe({
      next: (reservation) => {
        this.reservationState.set('success');
        this.messageService.add({
          severity: 'success',
          summary: 'Réservation créée',
          detail: `La réservation de « ${reservation.title.title} » a été créée.`,
        });
      },
      error: (err: unknown) => {
        this.reservationState.set('error');
        const appError = toAppError(err);
        this.reservationError.set(appError.message);
        this.messageService.add({ severity: 'error', summary: 'Erreur', detail: appError.message });
      },
    });
  }

  private loadTitle(id: number): void {
    this.loading.set(true);
    this.error.set(null);
    this.reservationState.set('idle');
    this.reservationError.set(null);

    this.catalogueApiService.getTitleById(id).subscribe({
      next: (value) => {
        this.title.set(value);
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

/**
 * `null` si l'identifiant de route est absent ou n'est pas un entier —
 * n'appelle alors jamais une API avec `NaN` (même précédent StaffUserDetailPage).
 */
function parseTitleId(rawId: string | null): number | null {
  if (rawId === null) {
    return null;
  }
  const id = Number(rawId);
  return Number.isInteger(id) ? id : null;
}
