import { Component, effect, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { Subject, debounceTime } from 'rxjs';

import { TitleResponse } from '../../../../catalogue/models/title-response';
import { StaffCatalogueApiService } from '../../../../catalogue/services/staff-catalogue-api.service';
import { AppError } from '../../../../core/errors/api-error';
import { toAppError } from '../../../../core/errors/api-error.util';
import { CreateReservationRequest } from '../../../../reservations/models/create-reservation-request';
import { ReservationResponse } from '../../../../reservations/models/reservation-response';
import { ReservationApiService } from '../../../../reservations/services/reservation-api.service';
import { ErrorState } from '../../../../shared/ui/error-state/error-state';
import { UserResponse } from '../../../../user/models/user-response';
import { UserApiService } from '../../../../user/services/user-api.service';

const SEARCH_DEBOUNCE_MS = 300;
const MEMBER_SEARCH_PAGE_SIZE = 20;
const TITLE_SEARCH_PAGE_SIZE = 20;


@Component({
  selector: 'app-staff-reservation-create-dialog',
  imports: [FormsModule, ButtonModule, DialogModule, InputTextModule, MessageModule, ErrorState],
  templateUrl: './staff-reservation-create-dialog.html',
  styleUrl: './staff-reservation-create-dialog.scss',
})
export class StaffReservationCreateDialog {
  private readonly userApiService = inject(UserApiService);
  private readonly staffCatalogueApiService = inject(StaffCatalogueApiService);
  private readonly reservationApiService = inject(ReservationApiService);
  private readonly messageService = inject(MessageService);

  readonly visible = input.required<boolean>();

  readonly closed = output<void>();
  readonly saved = output<ReservationResponse>();

  readonly memberSearchTerm = signal('');
  readonly memberSearchResults = signal<UserResponse[]>([]);
  readonly memberSearching = signal(false);
  readonly memberSearchError = signal<AppError | null>(null);
  readonly selectedMember = signal<UserResponse | null>(null);

  readonly titleSearchTerm = signal('');
  readonly titleSearchResults = signal<TitleResponse[]>([]);
  readonly titleSearching = signal(false);
  readonly titleSearchError = signal<AppError | null>(null);
  readonly selectedTitle = signal<TitleResponse | null>(null);

  readonly submitting = signal(false);
  readonly submitError = signal<string | null>(null);

  private readonly memberSearchInput$ = new Subject<string>();
  private readonly titleSearchInput$ = new Subject<string>();

  constructor() {
    
    
    // pour toute la durée de vie du composant — le dialog n'est jamais
    // détruit entre deux ouvertures, seuls les Signals sont réinitialisés
    // (`resetState()`). Avec `distinctUntilChanged()`, rechercher deux fois
    // le même membre/Title lors de deux sessions séparées du dialog
    // supprimait silencieusement la seconde recherche identique.
    // `debounceTime` seul suffit à limiter les appels HTTP.
    this.memberSearchInput$
      .pipe(debounceTime(SEARCH_DEBOUNCE_MS), takeUntilDestroyed())
      .subscribe((value) => this.runMemberSearch(value));

    this.titleSearchInput$
      .pipe(debounceTime(SEARCH_DEBOUNCE_MS), takeUntilDestroyed())
      .subscribe((value) => this.runTitleSearch(value));

    // Réinitialise systématiquement à chaque (ré)ouverture : jamais de
    // sélection obsolète d'une session précédente (même principe que
    // LoanCreateDialog).
    effect(() => {
      if (this.visible()) {
        this.resetState();
      }
    });
  }

  get canSubmit(): boolean {
    return this.selectedMember() !== null && this.selectedTitle() !== null && !this.submitting();
  }

  onMemberSearchInput(value: string): void {
    this.memberSearchTerm.set(value);
    this.memberSearchInput$.next(value);
  }

  /**
   * Un résultat sans `memberNumber` n'est jamais présenté comme un membre
   * valide : `GET /api/v1/users?q=` est générique et peut retourner des
   * non-adhérents.
   */
  selectMember(member: UserResponse): void {
    if (member.memberNumber === null) {
      return;
    }
    this.selectedMember.set(member);
  }

  changeMember(): void {
    this.selectedMember.set(null);
    this.memberSearchTerm.set('');
    this.memberSearchResults.set([]);
  }

  onTitleSearchInput(value: string): void {
    this.titleSearchTerm.set(value);
    this.titleSearchInput$.next(value);
  }

  selectTitle(title: TitleResponse): void {
    this.selectedTitle.set(title);
  }

  changeTitle(): void {
    this.selectedTitle.set(null);
    this.titleSearchTerm.set('');
    this.titleSearchResults.set([]);
  }

  cancel(): void {
    this.closed.emit();
  }

  submit(): void {
    if (this.submitting()) {
      return;
    }
    const member = this.selectedMember();
    const title = this.selectedTitle();
    if (member === null || title === null) {
      return;
    }

    this.submitting.set(true);
    this.submitError.set(null);

    const request: CreateReservationRequest = { userId: member.id, titleId: title.id };
    this.reservationApiService.createReservation(request).subscribe({
      next: (reservation) => {
        this.messageService.add({
          severity: 'success',
          summary: 'Réservation créée',
          detail: `La réservation de « ${reservation.title.title} » a été créée.`,
        });
        this.saved.emit(reservation);
        this.resetState();
      },
      error: (err: unknown) => {
        this.submitting.set(false);
        const appError = toAppError(err);
        this.submitError.set(appError.message);
        this.messageService.add({ severity: 'error', summary: 'Erreur', detail: appError.message });
      },
    });
  }

  private runMemberSearch(value: string): void {
    const trimmed = value.trim();
    if (trimmed.length === 0) {
      this.memberSearchResults.set([]);
      this.memberSearching.set(false);
      this.memberSearchError.set(null);
      return;
    }
    this.memberSearching.set(true);
    this.memberSearchError.set(null);
    this.userApiService.listUsers(0, MEMBER_SEARCH_PAGE_SIZE, trimmed).subscribe({
      next: (response) => {
        this.memberSearchResults.set(response.content);
        this.memberSearching.set(false);
      },
      error: (err: unknown) => {
        this.memberSearching.set(false);
        this.memberSearchError.set(toAppError(err));
      },
    });
  }

  private runTitleSearch(value: string): void {
    const trimmed = value.trim();
    if (trimmed.length === 0) {
      this.titleSearchResults.set([]);
      this.titleSearching.set(false);
      this.titleSearchError.set(null);
      return;
    }
    this.titleSearching.set(true);
    this.titleSearchError.set(null);
    this.staffCatalogueApiService.searchTitles({ q: trimmed, page: 0, size: TITLE_SEARCH_PAGE_SIZE }).subscribe({
      next: (response) => {
        this.titleSearchResults.set(response.content);
        this.titleSearching.set(false);
      },
      error: (err: unknown) => {
        this.titleSearching.set(false);
        this.titleSearchError.set(toAppError(err));
      },
    });
  }

  private resetState(): void {
    this.memberSearchTerm.set('');
    this.memberSearchResults.set([]);
    this.memberSearching.set(false);
    this.memberSearchError.set(null);
    this.selectedMember.set(null);

    this.titleSearchTerm.set('');
    this.titleSearchResults.set([]);
    this.titleSearching.set(false);
    this.titleSearchError.set(null);
    this.selectedTitle.set(null);

    this.submitting.set(false);
    this.submitError.set(null);
  }
}
