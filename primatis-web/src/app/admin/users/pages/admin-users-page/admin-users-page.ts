import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { Title } from '@angular/platform-browser';
import { RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { DatePickerModule } from 'primeng/datepicker';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TableModule, TableLazyLoadEvent } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { debounceTime, filter, map, merge, Subscription } from 'rxjs';

import { AppError } from '../../../../core/errors/api-error';
import { toAppError } from '../../../../core/errors/api-error.util';
import { dateToIsoDate } from '../../../../shared/date/iso-date';
import { orderedDateRange } from '../../../../shared/date/ordered-date-range';
import { EmptyState } from '../../../../shared/ui/empty-state/empty-state';
import { ErrorState } from '../../../../shared/ui/error-state/error-state';
import { LoadingState } from '../../../../shared/ui/loading-state/loading-state';
import {
  accountStatusSeverity as sharedAccountStatusSeverity,
  memberStatusSeverity as sharedMemberStatusSeverity,
  StatusTagSeverity,
} from '../../../../shared/status/status-severity';
import { AccountStatus } from '../../../../user/models/account-status';
import { MemberStatus } from '../../../../user/models/member-status';
import { UserDateFilters } from '../../../../user/models/user-date-filters';
import { UserResponse } from '../../../../user/models/user-response';
import { UserApiService } from '../../../../user/services/user-api.service';

const DEFAULT_PAGE_SIZE = 20;
const SEARCH_DEBOUNCE_MS = 300;

interface AccountStatusFilterOption {
  label: string;
  value: AccountStatus | null;
}

interface MemberStatusFilterOption {
  label: string;
  value: MemberStatus | null;
}

// Libellés français, valeurs = enums techniques du backend ; `null` = aucun filtre.
const ACCOUNT_STATUS_FILTER_OPTIONS: AccountStatusFilterOption[] = [
  { label: 'Tous les comptes', value: null },
  { label: 'Actifs', value: 'ACTIVE' },
  { label: 'Désactivés', value: 'DISABLED' },
];

const MEMBER_STATUS_FILTER_OPTIONS: MemberStatusFilterOption[] = [
  { label: 'Toutes les adhésions', value: null },
  { label: 'Actives', value: 'ACTIVE' },
  { label: 'Bloquées', value: 'BLOCKED' },
  { label: 'Expirées', value: 'EXPIRED' },
];


@Component({
  selector: 'app-admin-users-page',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    ButtonModule,
    DatePickerModule,
    InputTextModule,
    SelectModule,
    TableModule,
    TagModule,
    LoadingState,
    EmptyState,
    ErrorState,
  ],
  templateUrl: './admin-users-page.html',
  styleUrl: './admin-users-page.scss',
})
export class AdminUsersPage {
  private readonly userApiService = inject(UserApiService);
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly titleService = inject(Title);

  readonly accountStatusOptions = ACCOUNT_STATUS_FILTER_OPTIONS;
  readonly memberStatusOptions = MEMBER_STATUS_FILTER_OPTIONS;

  readonly filtersForm = this.formBuilder.group(
    {
      q: this.formBuilder.control(''),
      accountStatus: this.formBuilder.control<AccountStatus | null>(null),
      memberStatus: this.formBuilder.control<MemberStatus | null>(null),
      createdDateFrom: this.formBuilder.control<Date | null>(null),
      createdDateTo: this.formBuilder.control<Date | null>(null),
      memberExpirationDateFrom: this.formBuilder.control<Date | null>(null),
      memberExpirationDateTo: this.formBuilder.control<Date | null>(null),
    },
    {
      validators: [
        orderedDateRange('createdDateFrom', 'createdDateTo', 'createdDateRange'),
        orderedDateRange(
          'memberExpirationDateFrom',
          'memberExpirationDateTo',
          'memberExpirationDateRange',
        ),
      ],
    },
  );

  readonly rows = signal<UserResponse[]>([]);
  readonly totalRecords = signal(0);
  // Initialisé à true : le premier chargement est déclenché explicitement
  // ci-dessous (constructeur), jamais par le déclenchement automatique
  // PrimeNG au montage — le tableau n'est monté qu'une fois des données ou
  // une erreur disponibles (cf. template), il ne peut donc pas déclencher
  // lui-même ce premier appel.
  readonly loading = signal(true);
  readonly error = signal<AppError | null>(null);
  // Premier enregistrement affiché, lié au paginateur (retour page 0 après un
  // changement de critère) ; critères réellement appliqués à la dernière
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
    this.titleService.setTitle('Administration des utilisateurs — PRIMATIS');

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
      controls.accountStatus.valueChanges,
      controls.memberStatus.valueChanges,
      controls.createdDateFrom.valueChanges,
      controls.createdDateTo.valueChanges,
      controls.memberExpirationDateFrom.valueChanges,
      controls.memberExpirationDateTo.valueChanges,
    )
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.reloadFromCriteria());

    this.load(0, DEFAULT_PAGE_SIZE);
  }

  hasActiveFilters(): boolean {
    const raw = this.filtersForm.getRawValue();
    return (
      raw.q.trim().length > 0 ||
      raw.accountStatus !== null ||
      raw.memberStatus !== null ||
      !!raw.createdDateFrom ||
      !!raw.createdDateTo ||
      !!raw.memberExpirationDateFrom ||
      !!raw.memberExpirationDateTo
    );
  }

  /** Réinitialise tous les critères et recharge une seule fois, page 0. */
  clearFilters(): void {
    this.filtersForm.reset(
      {
        q: '',
        accountStatus: null,
        memberStatus: null,
        createdDateFrom: null,
        createdDateTo: null,
        memberExpirationDateFrom: null,
        memberExpirationDateTo: null,
      },
      { emitEvent: false },
    );
    this.load(0, this.lastSize);
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

  memberStatusSeverity(status: MemberStatus): StatusTagSeverity {
    return sharedMemberStatusSeverity(status);
  }

  accountStatusSeverity(status: AccountStatus): StatusTagSeverity {
    return sharedAccountStatusSeverity(status);
  }

  
  memberStatusLabel(status: MemberStatus): string {
    switch (status) {
      case 'ACTIVE':
        return 'Actif';
      case 'BLOCKED':
        return 'Bloqué';
      case 'EXPIRED':
        return 'Expiré';
    }
  }

  accountStatusLabel(status: AccountStatus): string {
    return status === 'ACTIVE' ? 'Actif' : 'Désactivé';
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
    if (this.filtersForm.invalid || this.criteriaKey() === this.appliedCriteriaKey) {
      return;
    }
    this.load(0, this.lastSize);
  }

  private criteriaKey(): string {
    const { q, accountStatus, memberStatus, dates } = this.currentCriteria();
    return JSON.stringify({ q, accountStatus, memberStatus, dates });
  }

  private currentCriteria(): {
    q: string;
    accountStatus: AccountStatus | null;
    memberStatus: MemberStatus | null;
    dates: UserDateFilters;
  } {
    const raw = this.filtersForm.getRawValue();
    const dates: UserDateFilters = {};
    const createdDateFrom = dateToIsoDate(raw.createdDateFrom);
    if (createdDateFrom) dates.createdDateFrom = createdDateFrom;
    const createdDateTo = dateToIsoDate(raw.createdDateTo);
    if (createdDateTo) dates.createdDateTo = createdDateTo;
    const memberExpirationDateFrom = dateToIsoDate(raw.memberExpirationDateFrom);
    if (memberExpirationDateFrom) dates.memberExpirationDateFrom = memberExpirationDateFrom;
    const memberExpirationDateTo = dateToIsoDate(raw.memberExpirationDateTo);
    if (memberExpirationDateTo) dates.memberExpirationDateTo = memberExpirationDateTo;
    return {
      q: raw.q.trim(),
      accountStatus: raw.accountStatus,
      memberStatus: raw.memberStatus,
      dates,
    };
  }

  private load(page: number, size: number): void {
    this.lastPage = page;
    this.lastSize = size;
    this.first.set(page * size);
    this.loading.set(true);
    this.error.set(null);

    const { q, accountStatus, memberStatus, dates } = this.currentCriteria();
    this.appliedQuery = q;
    this.appliedCriteriaKey = this.criteriaKey();
    this.filtersApplied.set(this.hasActiveFilters());

    // Une réponse tardive d'une requête devenue obsolète ne doit jamais
    // écraser le résultat de la requête la plus récente.
    this.request?.unsubscribe();
    this.request = this.userApiService
      .listUsers(page, size, q || undefined, accountStatus ?? undefined, memberStatus ?? undefined, dates)
      .subscribe({
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
