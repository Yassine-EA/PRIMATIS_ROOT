import { CurrencyPipe } from '@angular/common';
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
import { FineResponse } from '../../../../fines/models/fine-response';
import { FineSearchFilters } from '../../../../fines/models/fine-search-filters';
import { FineStatus } from '../../../../fines/models/fine-status';
import { FineApiService } from '../../../../fines/services/fine-api.service';
import { formatUtcDateTime } from '../../../../shared/date/format-utc-date-time';
import { dateToIsoDate } from '../../../../shared/date/iso-date';
import { orderedDateRange } from '../../../../shared/date/ordered-date-range';
import {
  fineStatusSeverity as sharedFineStatusSeverity,
  StatusTagSeverity,
} from '../../../../shared/status/status-severity';
import { EmptyState } from '../../../../shared/ui/empty-state/empty-state';
import { ErrorState } from '../../../../shared/ui/error-state/error-state';
import { LoadingState } from '../../../../shared/ui/loading-state/loading-state';

const DEFAULT_PAGE_SIZE = 20;
const SEARCH_DEBOUNCE_MS = 300;

interface FineStatusFilterOption {
  label: string;
  value: FineStatus | null;
}

// Mêmes libellés que les tags de statut du tableau (fineStatusLabel) ;
// valeurs = enum technique FineStatus du backend.
const FINE_STATUS_FILTER_OPTIONS: FineStatusFilterOption[] = [
  { label: 'Tous les statuts', value: null },
  { label: 'Impayée', value: 'UNPAID' },
  { label: 'Payée', value: 'PAID' },
  { label: 'Annulée', value: 'CANCELLED' },
];


@Component({
  selector: 'app-staff-fines-page',
  imports: [
    ReactiveFormsModule,
    InputTextModule,
    DatePickerModule,
    SelectModule,
    TableModule,
    TagModule,
    ButtonModule,
    CurrencyPipe,
    LoadingState,
    EmptyState,
    ErrorState,
  ],
  templateUrl: './staff-fines-page.html',
  styleUrl: './staff-fines-page.scss',
})
export class StaffFinesPage {
  private readonly fineApiService = inject(FineApiService);
  private readonly authService = inject(AuthService);
  private readonly messageService = inject(MessageService);
  private readonly confirmationService = inject(ConfirmationService);
  private readonly titleService = inject(Title);
  private readonly formBuilder = inject(NonNullableFormBuilder);

  readonly fineStatusOptions = FINE_STATUS_FILTER_OPTIONS;

  readonly filtersForm = this.formBuilder.group(
    {
      q: this.formBuilder.control(''),
      fineStatus: this.formBuilder.control<FineStatus | null>(null),
      issuedDateFrom: this.formBuilder.control<Date | null>(null),
      issuedDateTo: this.formBuilder.control<Date | null>(null),
      closedDateFrom: this.formBuilder.control<Date | null>(null),
      closedDateTo: this.formBuilder.control<Date | null>(null),
    },
    {
      validators: [
        orderedDateRange('issuedDateFrom', 'issuedDateTo', 'issuedDateRange'),
        orderedDateRange('closedDateFrom', 'closedDateTo', 'closedDateRange'),
      ],
    },
  );

  readonly rows = signal<FineResponse[]>([]);
  readonly totalRecords = signal(0);
  // Initialisé à true : le premier chargement est déclenché explicitement
  // ci-dessous (constructeur), jamais par le déclenchement automatique
  // PrimeNG au montage — même principe que StaffReservationsPage.
  readonly loading = signal(true);
  readonly error = signal<AppError | null>(null);
  readonly payingFineId = signal<number | null>(null);
  readonly cancellingFineId = signal<number | null>(null);

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
    this.titleService.setTitle('Amendes — PRIMATIS');

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
      controls.fineStatus.valueChanges,
      controls.issuedDateFrom.valueChanges,
      controls.issuedDateTo.valueChanges,
      controls.closedDateFrom.valueChanges,
      controls.closedDateTo.valueChanges,
    )
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.reloadFromCriteria());

    this.load(0, DEFAULT_PAGE_SIZE);
  }

  hasActiveFilters(): boolean {
    const raw = this.filtersForm.getRawValue();
    return (
      raw.q.trim().length > 0 ||
      raw.fineStatus !== null ||
      !!raw.issuedDateFrom ||
      !!raw.issuedDateTo ||
      !!raw.closedDateFrom ||
      !!raw.closedDateTo
    );
  }

  /** Réinitialise tous les critères et recharge une seule fois, page 0. */
  clearFilters(): void {
    this.filtersForm.reset(
      { q: '', fineStatus: null, issuedDateFrom: null, issuedDateTo: null, closedDateFrom: null, closedDateTo: null },
      { emitEvent: false },
    );
    this.load(0, this.lastSize);
  }

  /** Instant ISO → `jj/mm/aaaa hh:mm UTC` ; `null` → `—`. Jamais la chaîne ISO brute. */
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

  private currentFilters(): FineSearchFilters {
    const raw = this.filtersForm.getRawValue();
    const filters: FineSearchFilters = {};
    const q = raw.q.trim();
    if (q) filters.q = q;
    if (raw.fineStatus) filters.fineStatus = raw.fineStatus;
    const issuedDateFrom = dateToIsoDate(raw.issuedDateFrom);
    if (issuedDateFrom) filters.issuedDateFrom = issuedDateFrom;
    const issuedDateTo = dateToIsoDate(raw.issuedDateTo);
    if (issuedDateTo) filters.issuedDateTo = issuedDateTo;
    const closedDateFrom = dateToIsoDate(raw.closedDateFrom);
    if (closedDateFrom) filters.closedDateFrom = closedDateFrom;
    const closedDateTo = dateToIsoDate(raw.closedDateTo);
    if (closedDateTo) filters.closedDateTo = closedDateTo;
    return filters;
  }

  /**
   * Après une action réussie : sans critère actif, la ligne est remplacée par
   * la réponse serveur ; avec un critère actif (statut, clôture…), la ligne
   * peut ne plus correspondre → rechargement de la page courante avec les
   * mêmes critères (jamais un remplacement local qui laisserait une ligne
   * périmée visible).
   */
  private applyUpdatedFine(updated: FineResponse): void {
    if (this.filtersApplied()) {
      this.load(this.lastPage, this.lastSize);
      return;
    }
    this.rows.set(this.rows().map((row) => (row.id === updated.id ? updated : row)));
  }

  get canManageFines(): boolean {
    return this.authService.hasPermission('FINE_MANAGE');
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

  
  // mapping exact que la fonction locale précédente, non centralisée par
  
  fineStatusSeverity(status: FineStatus): StatusTagSeverity {
    return sharedFineStatusSeverity(status);
  }

  fineStatusLabel(status: FineStatus): string {
    switch (status) {
      case 'UNPAID':
        return 'Impayée';
      case 'PAID':
        return 'Payée';
      case 'CANCELLED':
        return 'Annulée';
    }
  }

  /**
   * Règle de visibilité UI, pas une règle métier : le statut affiché est
   * celui déjà reçu du backend, jamais recalculé.
   */
  isActionable(fine: FineResponse): boolean {
    return fine.fineStatus === 'UNPAID';
  }

  /** Anti-double-clic UI uniquement, propre à cette ligne. */
  isBusy(fine: FineResponse): boolean {
    return this.payingFineId() === fine.id || this.cancellingFineId() === fine.id;
  }

  confirmPaymentPrompt(fine: FineResponse): void {
    this.confirmationService.confirm({
      header: 'Confirmation du paiement',
      message: `Confirmer le paiement de l'amende de ${fine.borrower.firstName} ${fine.borrower.lastName} ?`,
      accept: () => this.performConfirmPayment(fine),
    });
  }

  confirmCancelPrompt(fine: FineResponse): void {
    this.confirmationService.confirm({
      header: 'Annulation',
      message: `Confirmer l'annulation de l'amende de ${fine.borrower.firstName} ${fine.borrower.lastName} ?`,
      accept: () => this.performCancel(fine),
    });
  }

  private performConfirmPayment(fine: FineResponse): void {
    this.payingFineId.set(fine.id);
    this.fineApiService.confirmPayment(fine.id).subscribe({
      next: (updated) => {
        this.payingFineId.set(null);
        this.applyUpdatedFine(updated);
        this.messageService.add({
          severity: 'success',
          summary: 'Paiement confirmé',
          detail: `Le paiement de l'amende de ${updated.borrower.firstName} ${updated.borrower.lastName} a été confirmé.`,
        });
      },
      error: (err: unknown) => {
        this.payingFineId.set(null);
        this.messageService.add({ severity: 'error', summary: 'Erreur', detail: toAppError(err).message });
      },
    });
  }

  private performCancel(fine: FineResponse): void {
    this.cancellingFineId.set(fine.id);
    this.fineApiService.cancelFine(fine.id).subscribe({
      next: (updated) => {
        this.cancellingFineId.set(null);
        this.applyUpdatedFine(updated);
        this.messageService.add({
          severity: 'success',
          summary: 'Amende annulée',
          detail: `L'amende de ${updated.borrower.firstName} ${updated.borrower.lastName} a été annulée.`,
        });
      },
      error: (err: unknown) => {
        this.cancellingFineId.set(null);
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
    this.appliedQuery = filters.q ?? '';
    this.appliedCriteriaKey = JSON.stringify(filters);
    this.filtersApplied.set(Object.keys(filters).length > 0);

    // Une réponse tardive d'une requête devenue obsolète ne doit jamais
    // écraser le résultat de la requête la plus récente.
    this.request?.unsubscribe();
    this.request = this.fineApiService.listFines(page, size, filters).subscribe({
      next: (response) => {
        // Page devenue vide après une action (dernière ligne d'une dernière page
        // qui ne correspond plus au filtre) : on recule d'une page plutôt que
        // d'afficher « aucun résultat » alors qu'il en reste.
        if (response.content.length === 0 && page > 0 && response.totalElements > 0) {
          this.load(Math.min(page - 1, Math.max(0, response.totalPages - 1)), size);
          return;
        }
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
