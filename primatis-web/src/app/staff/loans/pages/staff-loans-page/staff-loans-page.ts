import { DatePipe } from '@angular/common';
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
import { LoanResponse } from '../../../../loans/models/loan-response';
import { LoanSearchFilters } from '../../../../loans/models/loan-search-filters';
import { LoanStatus } from '../../../../loans/models/loan-status';
import { LoanApiService } from '../../../../loans/services/loan-api.service';
import { dateToIsoDate } from '../../../../shared/date/iso-date';
import { orderedDateRange } from '../../../../shared/date/ordered-date-range';
import { EmptyState } from '../../../../shared/ui/empty-state/empty-state';
import { ErrorState } from '../../../../shared/ui/error-state/error-state';
import { LoadingState } from '../../../../shared/ui/loading-state/loading-state';
import { loanStatusSeverity as sharedLoanStatusSeverity, StatusTagSeverity } from '../../../../shared/status/status-severity';
import { LoanCreateDialog } from '../../components/loan-create-dialog/loan-create-dialog';

const DEFAULT_PAGE_SIZE = 20;
const SEARCH_DEBOUNCE_MS = 300;

interface LoanStatusFilterOption {
  label: string;
  value: LoanStatus | null;
}

// Libellés français, valeurs = enum technique LoanStatus du backend.
const LOAN_STATUS_FILTER_OPTIONS: LoanStatusFilterOption[] = [
  { label: 'Tous les statuts', value: null },
  { label: 'En cours', value: 'ACTIVE' },
  { label: 'En retard', value: 'OVERDUE' },
  { label: 'Retournés', value: 'RETURNED' },
];

const OPEN_LOAN_STATUSES: readonly LoanStatus[] = ['ACTIVE', 'OVERDUE'];


@Component({
  selector: 'app-staff-loans-page',
  imports: [
    ReactiveFormsModule,
    InputTextModule,
    DatePickerModule,
    SelectModule,
    TableModule,
    TagModule,
    ButtonModule,
    DatePipe,
    LoadingState, EmptyState, ErrorState, LoanCreateDialog],
  templateUrl: './staff-loans-page.html',
  styleUrl: './staff-loans-page.scss',
})
export class StaffLoansPage {
  private readonly loanApiService = inject(LoanApiService);
  private readonly authService = inject(AuthService);
  private readonly messageService = inject(MessageService);
  private readonly confirmationService = inject(ConfirmationService);
  private readonly titleService = inject(Title);
  private readonly formBuilder = inject(NonNullableFormBuilder);

  readonly loanStatusOptions = LOAN_STATUS_FILTER_OPTIONS;

  readonly filtersForm = this.formBuilder.group(
    {
      q: this.formBuilder.control(''),
      loanStatus: this.formBuilder.control<LoanStatus | null>(null),
      loanDateFrom: this.formBuilder.control<Date | null>(null),
      loanDateTo: this.formBuilder.control<Date | null>(null),
      dueDateFrom: this.formBuilder.control<Date | null>(null),
      dueDateTo: this.formBuilder.control<Date | null>(null),
    },
    {
      validators: [
        orderedDateRange('loanDateFrom', 'loanDateTo', 'loanDateRange'),
        orderedDateRange('dueDateFrom', 'dueDateTo', 'dueDateRange'),
      ],
    },
  );

  readonly rows = signal<LoanResponse[]>([]);
  readonly totalRecords = signal(0);
  // Même principe que StaffUsersPage/MemberLoansPage : premier chargement
  // déclenché explicitement dans le constructeur, jamais par le montage
  // automatique PrimeNG (le tableau n'est monté qu'une fois des données ou
  // une erreur disponibles, cf. template).
  readonly loading = signal(true);
  readonly error = signal<AppError | null>(null);
  readonly returningLoanId = signal<number | null>(null);
  readonly createDialogVisible = signal(false);

  // Premier enregistrement affiché, lié au paginateur (retour page 0 après
  // un changement de critère) ; critères réellement appliqués à la dernière
  // requête, pour l'état vide (distincts de la saisie non encore debouncée).
  readonly first = signal(0);
  readonly filtersApplied = signal(false);

  private lastPage = 0;
  private lastSize = DEFAULT_PAGE_SIZE;
  // Dernier `q` (trimé) réellement envoyé : équivalent d'un
  // distinctUntilChanged qui reste correct après « Effacer ».
  private appliedQuery = '';
  // Critères effectifs de la dernière requête (JSON) : `p-datepicker` réémet
  // `null` à chaque frappe partielle d'une date ; sans cette comparaison,
  // chaque caractère relancerait une requête identique.
  private appliedCriteriaKey = '';
  private request: Subscription | null = null;

  constructor() {
    this.titleService.setTitle('Prêts et retours — PRIMATIS');

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
      controls.loanStatus.valueChanges,
      controls.loanDateFrom.valueChanges,
      controls.loanDateTo.valueChanges,
      controls.dueDateFrom.valueChanges,
      controls.dueDateTo.valueChanges,
    )
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.reloadFromCriteria());

    this.load(0, DEFAULT_PAGE_SIZE);
  }

  hasActiveFilters(): boolean {
    const raw = this.filtersForm.getRawValue();
    return (
      raw.q.trim().length > 0 ||
      raw.loanStatus !== null ||
      !!raw.loanDateFrom ||
      !!raw.loanDateTo ||
      !!raw.dueDateFrom ||
      !!raw.dueDateTo
    );
  }

  /** Réinitialise tous les critères et recharge une seule fois, page 0. */
  clearFilters(): void {
    this.filtersForm.reset(
      { q: '', loanStatus: null, loanDateFrom: null, loanDateTo: null, dueDateFrom: null, dueDateTo: null },
      { emitEvent: false },
    );
    this.load(0, this.lastSize);
  }

  /**
   * Changement d'un critère : retour page 0. Un intervalle incohérent
   * n'appelle pas le backend (message affiché sous le groupe de dates) ;
   * la liste précédente reste affichée jusqu'à correction.
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

  private currentFilters(): LoanSearchFilters {
    const raw = this.filtersForm.getRawValue();
    const filters: LoanSearchFilters = {};
    const q = raw.q.trim();
    if (q) filters.q = q;
    if (raw.loanStatus) filters.loanStatus = raw.loanStatus;
    const loanDateFrom = dateToIsoDate(raw.loanDateFrom);
    if (loanDateFrom) filters.loanDateFrom = loanDateFrom;
    const loanDateTo = dateToIsoDate(raw.loanDateTo);
    if (loanDateTo) filters.loanDateTo = loanDateTo;
    const dueDateFrom = dateToIsoDate(raw.dueDateFrom);
    if (dueDateFrom) filters.dueDateFrom = dueDateFrom;
    const dueDateTo = dateToIsoDate(raw.dueDateTo);
    if (dueDateTo) filters.dueDateTo = dueDateTo;
    return filters;
  }

  get canManageLoans(): boolean {
    return this.authService.hasPermission('LOAN_MANAGE');
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

  loanStatusSeverity(status: LoanStatus): StatusTagSeverity {
    return sharedLoanStatusSeverity(status);
  }

  loanStatusLabel(status: LoanStatus): string {
    switch (status) {
      case 'ACTIVE':
        return 'En cours';
      case 'OVERDUE':
        return 'En retard';
      case 'RETURNED':
        return 'Retourné';
    }
  }

  isReturnable(loan: LoanResponse): boolean {
    return OPEN_LOAN_STATUSES.includes(loan.loanStatus);
  }

  openCreateDialog(): void {
    this.createDialogVisible.set(true);
  }

  closeCreateDialog(): void {
    this.createDialogVisible.set(false);
  }

  /**
   * `loan` (le `LoanResponse` exact renvoyé par `registerLoan`) n'est pas
   * inséré localement : la page courante est rechargée pour rester
   * cohérente avec le tri serveur (`loanDate` décroissant).
   */
  onLoanCreated(loan: LoanResponse): void {
    this.createDialogVisible.set(false);
    this.load(this.lastPage, this.lastSize);
  }

  confirmReturn(loan: LoanResponse): void {
    this.confirmationService.confirm({
      header: 'Retour',
      message: `Confirmer le retour de l'exemplaire ${loan.copy.inventoryCode} ?`,
      accept: () => this.performReturn(loan),
    });
  }

  private performReturn(loan: LoanResponse): void {
    this.returningLoanId.set(loan.id);
    this.loanApiService.registerReturn(loan.id).subscribe({
      next: (updated) => {
        this.returningLoanId.set(null);
        this.rows.set(this.rows().map((candidate) => (candidate.id === updated.id ? updated : candidate)));
        this.messageService.add({
          severity: 'success',
          summary: 'Prêt retourné',
          detail: `Le retour de l'exemplaire ${updated.copy.inventoryCode} a été enregistré.`,
        });
      },
      error: (err: unknown) => {
        this.returningLoanId.set(null);
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
    this.request = this.loanApiService.listLoans(page, size, filters).subscribe({
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
