import { Component, computed, inject, signal } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { RouterLink } from '@angular/router';
import { ConfirmationService, MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { PaginatorModule, PaginatorState } from 'primeng/paginator';
import { TableLazyLoadEvent } from 'primeng/table';
import { TagModule } from 'primeng/tag';

import { AppError } from '../../../../core/errors/api-error';
import { toAppError } from '../../../../core/errors/api-error.util';
import { ReservationResponse } from '../../../../reservations/models/reservation-response';
import { ReservationStatus } from '../../../../reservations/models/reservation-status';
import { ReservationApiService } from '../../../../reservations/services/reservation-api.service';
import { EmptyState } from '../../../../shared/ui/empty-state/empty-state';
import { ErrorState } from '../../../../shared/ui/error-state/error-state';
import { LoadingState } from '../../../../shared/ui/loading-state/loading-state';
import {
  reservationStatusSeverity as sharedReservationStatusSeverity,
  StatusTagSeverity,
} from '../../../../shared/status/status-severity';
import { ReservationCreateDialog } from '../../components/reservation-create-dialog/reservation-create-dialog';
import { MemberCoverDirective } from '../../../member-cover.directive';

const DEFAULT_PAGE_SIZE = 20;
const CANCELLABLE_STATUSES: readonly ReservationStatus[] = ['WAITING', 'READY'];


@Component({
  selector: 'app-member-reservations-page',
  imports: [
    RouterLink,
    TagModule,
    ButtonModule,
    PaginatorModule,
    LoadingState,
    EmptyState,
    ErrorState,
    ReservationCreateDialog,
    MemberCoverDirective,
  ],
  templateUrl: './member-reservations-page.html',
  styleUrl: './member-reservations-page.scss',
})
export class MemberReservationsPage {
  private readonly reservationApiService = inject(ReservationApiService);
  private readonly messageService = inject(MessageService);
  private readonly confirmationService = inject(ConfirmationService);
  private readonly titleService = inject(Title);

  readonly rows = signal<ReservationResponse[]>([]);
  readonly totalRecords = signal(0);
  // Initialisé à true : le premier chargement est déclenché explicitement
  // ci-dessous (constructeur), jamais par le déclenchement automatique
  // PrimeNG au montage — même principe que MemberLoansPage/StaffLoansPage.
  readonly loading = signal(true);
  readonly error = signal<AppError | null>(null);
  readonly cancellingReservationId = signal<number | null>(null);
  readonly createDialogVisible = signal(false);
  readonly currentFirst = signal(0);
  readonly pageSize = DEFAULT_PAGE_SIZE;
  readonly waitingCount = computed(
    () => this.rows().filter((reservation) => reservation.reservationStatus === 'WAITING').length,
  );
  readonly readyCount = computed(
    () => this.rows().filter((reservation) => reservation.reservationStatus === 'READY').length,
  );

  private lastPage = 0;
  private lastSize = DEFAULT_PAGE_SIZE;

  constructor() {
    this.titleService.setTitle('Mes réservations — PRIMATIS');
    this.load(0, DEFAULT_PAGE_SIZE);
  }

  /**
   * `event.first`/`event.rows` sont optionnels dans le typage PrimeNG :
   * toujours retomber sur des valeurs par défaut explicites plutôt que de
   * propager `undefined` vers le backend.
   */
  onLazyLoad(event: TableLazyLoadEvent): void {
    const rows = event.rows ?? DEFAULT_PAGE_SIZE;
    const first = event.first ?? 0;
    this.load(Math.floor(first / rows), rows);
  }

  onPageChange(event: PaginatorState): void {
    const rows = event.rows ?? DEFAULT_PAGE_SIZE;
    const first = event.first ?? 0;
    this.currentFirst.set(first);
    this.load(Math.floor(first / rows), rows);
  }

  retry(): void {
    this.load(this.lastPage, this.lastSize);
  }

  reservationStatusSeverity(status: ReservationStatus): StatusTagSeverity {
    return sharedReservationStatusSeverity(status);
  }

  reservationStatusLabel(status: ReservationStatus): string {
    switch (status) {
      case 'WAITING':
        return 'En attente';
      case 'READY':
        return 'Prête';
      case 'FULFILLED':
        return 'Honorée';
      case 'CANCELLED':
        return 'Annulée';
      case 'EXPIRED':
        return 'Expirée';
    }
  }

  
  isCancellable(reservation: ReservationResponse): boolean {
    return CANCELLABLE_STATUSES.includes(reservation.reservationStatus);
  }

  openCreateDialog(): void {
    this.createDialogVisible.set(true);
  }

  closeCreateDialog(): void {
    this.createDialogVisible.set(false);
  }

  /**
   * `reservation` (le `ReservationResponse` exact renvoyé par
   * `createOwnReservation`) n'est pas inséré localement : la page revient
   * à la page 0 et se recharge depuis le serveur, cohérente avec le tri
   * `reservationDate DESC` (voir Javadoc de la classe).
   */
  onReservationCreated(reservation: ReservationResponse): void {
    this.createDialogVisible.set(false);
    this.currentFirst.set(0);
    this.load(0, this.lastSize);
  }

  formatDate(value: string | null): string {
    if (!value) {
      return '—';
    }

    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
      return value;
    }

    return new Intl.DateTimeFormat('fr-BE', {
      day: 'numeric',
      month: 'short',
      year: 'numeric',
      timeZone: 'UTC',
    })
      .format(date)
      .replace('.', '');
  }

  confirmCancel(reservation: ReservationResponse): void {
    this.confirmationService.confirm({
      header: 'Annulation',
      message: `Confirmer l'annulation de la réservation de « ${reservation.title.title} » ?`,
      accept: () => this.performCancel(reservation),
    });
  }

  private performCancel(reservation: ReservationResponse): void {
    this.cancellingReservationId.set(reservation.id);
    this.reservationApiService.cancelOwnReservation(reservation.id).subscribe({
      next: (updated) => {
        this.cancellingReservationId.set(null);
        this.rows.set(this.rows().map((row) => (row.id === updated.id ? updated : row)));
        this.messageService.add({
          severity: 'success',
          summary: 'Réservation annulée',
          detail: `La réservation de « ${updated.title.title} » a été annulée.`,
        });
      },
      error: (err: unknown) => {
        this.cancellingReservationId.set(null);
        this.messageService.add({
          severity: 'error',
          summary: 'Erreur',
          detail: toAppError(err).message,
        });
      },
    });
  }

  private load(page: number, size: number): void {
    this.lastPage = page;
    this.lastSize = size;
    this.loading.set(true);
    this.error.set(null);

    this.reservationApiService.listOwnReservations(page, size).subscribe({
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
