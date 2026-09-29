import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { Title } from '@angular/platform-browser';
import { ConfirmationService, MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { DatePickerModule } from 'primeng/datepicker';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TableLazyLoadEvent, TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { debounceTime, filter, map, merge, Subscription } from 'rxjs';

import { AuthService } from '../../../../auth/services/auth.service';
import { AppError } from '../../../../core/errors/api-error';
import { toAppError } from '../../../../core/errors/api-error.util';
import { ReservationResponse } from '../../../../reservations/models/reservation-response';
import { ReservationSearchFilters } from '../../../../reservations/models/reservation-search-filters';
import { ReservationStatus } from '../../../../reservations/models/reservation-status';
import { ReservationApiService } from '../../../../reservations/services/reservation-api.service';
import { formatUtcDateTime } from '../../../../shared/date/format-utc-date-time';
import { dateToIsoDate } from '../../../../shared/date/iso-date';
import { orderedDateRange } from '../../../../shared/date/ordered-date-range';
import { EmptyState } from '../../../../shared/ui/empty-state/empty-state';
import { ErrorState } from '../../../../shared/ui/error-state/error-state';
import { LoadingState } from '../../../../shared/ui/loading-state/loading-state';
import {
  reservationStatusSeverity as sharedReservationStatusSeverity,
  StatusTagSeverity,
} from '../../../../shared/status/status-severity';
import { StaffReservationCreateDialog } from '../../components/staff-reservation-create-dialog/staff-reservation-create-dialog';

const DEFAULT_PAGE_SIZE = 20;
const SEARCH_DEBOUNCE_MS = 300;

interface ReservationStatusFilterOption {
  label: string;
  value: ReservationStatus | null;
}

// Mêmes libellés que les tags de statut du tableau (reservationStatusLabel) ;
// valeurs = enum technique ReservationStatus du backend.
const RESERVATION_STATUS_FILTER_OPTIONS: ReservationStatusFilterOption[] = [
  { label: 'Tous les statuts', value: null },
  { label: 'En attente', value: 'WAITING' },
  { label: 'Prête', value: 'READY' },
  { label: 'Honorée', value: 'FULFILLED' },
  { label: 'Annulée', value: 'CANCELLED' },
  { label: 'Expirée', value: 'EXPIRED' },
];

const CANCELLABLE_STATUSES: readonly ReservationStatus[] = ['WAITING', 'READY'];


@Component({
  selector: 'app-staff-reservations-page',
  imports: [
    ReactiveFormsModule,
    InputTextModule,
    DatePickerModule,
    SelectModule,
    TableModule,
    TagModule,
    ButtonModule,
    LoadingState,
    EmptyState,
    ErrorState,
    StaffReservationCreateDialog,
  ],
  templateUrl: './staff-reservations-page.html',
  styleUrl: './staff-reservations-page.scss',
})
export class StaffReservationsPage {
  private readonly reservationApiService = inject(ReservationApiService);
  private readonly authService = inject(AuthService);
  private readonly messageService = inject(MessageService);
  private readonly confirmationService = inject(ConfirmationService);
  private readonly titleService = inject(Title);
  private readonly formBuilder = inject(NonNullableFormBuilder);

  readonly reservationStatusOptions = RESERVATION_STATUS_FILTER_OPTIONS;

  readonly filtersForm = this.formBuilder.group(
    {
      q: this.formBuilder.control(''),
      reservationStatus: this.formBuilder.control<ReservationStatus | null>(null),
      reservationDateFrom: this.formBuilder.control<Date | null>(null),
      reservationDateTo: this.formBuilder.control<Date | null>(null),
      expirationDateFrom: this.formBuilder.control<Date | null>(null),
      expirationDateTo: this.formBuilder.control<Date | null>(null),
    },
    {
      validators: [
        orderedDateRange('reservationDateFrom', 'reservationDateTo', 'reservationDateRange'),
        orderedDateRange('expirationDateFrom', 'expirationDateTo', 'expirationDateRange'),
      ],
    },
  );

  readonly rows = signal<ReservationResponse[]>([]);
  readonly totalRecords = signal(0);
  // Initialisé à true : le premier chargement est déclenché explicitement
  // ci-dessous (constructeur), jamais par le déclenchement automatique
  // PrimeNG au montage — même principe que StaffLoansPage/MemberReservationsPage.
  readonly loading = signal(true);
  readonly error = signal<AppError | null>(null);
  readonly cancellingReservationId = signal<number | null>(null);
  readonly createDialogVisible = signal(false);

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
    this.titleService.setTitle('Réservations — PRIMATIS');

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
      controls.reservationStatus.valueChanges,
      controls.reservationDateFrom.valueChanges,
      controls.reservationDateTo.valueChanges,
      controls.expirationDateFrom.valueChanges,
      controls.expirationDateTo.valueChanges,
    )
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.reloadFromCriteria());

    this.load(0, DEFAULT_PAGE_SIZE);
  }

  hasActiveFilters(): boolean {
    const raw = this.filtersForm.getRawValue();
    return (
      raw.q.trim().length > 0 ||
      raw.reservationStatus !== null ||
      !!raw.reservationDateFrom ||
      !!raw.reservationDateTo ||
      !!raw.expirationDateFrom ||
      !!raw.expirationDateTo
    );
  }

  /** Réinitialise tous les critères et recharge une seule fois, page 0. */
  clearFilters(): void {
    this.filtersForm.reset(
      {
        q: '',
        reservationStatus: null,
        reservationDateFrom: null,
        reservationDateTo: null,
        expirationDateFrom: null,
        expirationDateTo: null,
      },
      { emitEvent: false },
    );
    this.load(0, this.lastSize);
  }

  /**
   * Instant ISO → `jj/mm/aaaa hh:mm UTC` ; `null` (ex. WAITING sans
   * expiration) → `—`. Jamais la chaîne ISO brute.
   */
  formatDateTime(value: string | null): string {
    return formatUtcDateTime(value);
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
    if (this.filtersForm.invalid || JSON.stringify(this.currentFilters()) === this.appliedCriteriaKey) {
      return;
    }
    this.load(0, this.lastSize);
  }

  private currentFilters(): ReservationSearchFilters {
    const raw = this.filtersForm.getRawValue();
    const filters: ReservationSearchFilters = {};
    const q = raw.q.trim();
    if (q) filters.q = q;
    if (raw.reservationStatus) filters.reservationStatus = raw.reservationStatus;
    const reservationDateFrom = dateToIsoDate(raw.reservationDateFrom);
    if (reservationDateFrom) filters.reservationDateFrom = reservationDateFrom;
    const reservationDateTo = dateToIsoDate(raw.reservationDateTo);
    if (reservationDateTo) filters.reservationDateTo = reservationDateTo;
    const expirationDateFrom = dateToIsoDate(raw.expirationDateFrom);
    if (expirationDateFrom) filters.expirationDateFrom = expirationDateFrom;
    const expirationDateTo = dateToIsoDate(raw.expirationDateTo);
    if (expirationDateTo) filters.expirationDateTo = expirationDateTo;
    return filters;
  }

  get canManageReservations(): boolean {
    return this.authService.hasPermission('RESERVATION_MANAGE');
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

  retry(): void {
    this.load(this.lastPage, this.lastSize);
  }

  reservationStatusSeverity(status: ReservationStatus): StatusTagSeverity {
    return sharedReservationStatusSeverity(status);
  }

  // Même précédent exact que MemberReservationsPage.reservationStatusLabel
  
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

  /**
   * Règle de visibilité UI, pas une règle métier : le statut affiché est
   * celui déjà reçu du backend, jamais recalculé.
   */
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
   * `createReservation`) n'est pas inséré localement : la page courante
   * est rechargée — voir Javadoc de la classe pour la justification du
   * choix « page courante » plutôt que « page 0 ».
   */
  onReservationCreated(reservation: ReservationResponse): void {
    this.createDialogVisible.set(false);
    this.load(this.lastPage, this.lastSize);
  }

  confirmCancel(reservation: ReservationResponse): void {
    this.confirmationService.confirm({
      header: 'Annulation',
      message: `Confirmer l'annulation de la réservation de ${reservation.member.firstName} ${reservation.member.lastName} pour « ${reservation.title.title} » ?`,
      accept: () => this.performCancel(reservation),
    });
  }

  private performCancel(reservation: ReservationResponse): void {
    this.cancellingReservationId.set(reservation.id);
    this.reservationApiService.cancelReservation(reservation.id).subscribe({
      next: (updated) => {
        this.cancellingReservationId.set(null);
        this.rows.set(this.rows().map((row) => (row.id === updated.id ? updated : row)));
        this.messageService.add({
          severity: 'success',
          summary: 'Réservation annulée',
          detail: `La réservation de ${updated.member.firstName} ${updated.member.lastName} pour « ${updated.title.title} » a été annulée.`,
        });
      },
      error: (err: unknown) => {
        this.cancellingReservationId.set(null);
        this.messageService.add({ severity: 'error', summary: 'Erreur', detail: toAppError(err).message });
      },
    });
  }

  private load(page: number, size: number): void {
    this.lastPage = page;
    this.lastSize = size;
    this.first.set(page * size);
    this.loading.set(true);
    this.error.set(null);

    const filters = this.currentFilters();
    const q = filters.q ?? '';
    this.appliedQuery = q;
    this.appliedCriteriaKey = JSON.stringify(filters);
    this.filtersApplied.set(Object.keys(filters).length > 0);

    // Une réponse tardive d'une requête devenue obsolète ne doit jamais
    // écraser le résultat de la requête la plus récente.
    this.request?.unsubscribe();
    this.request = this.reservationApiService.listReservations(page, size, filters).subscribe({
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
