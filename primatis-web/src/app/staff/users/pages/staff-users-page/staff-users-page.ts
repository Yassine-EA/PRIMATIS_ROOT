import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { Title } from '@angular/platform-browser';
import { RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TableModule, TableLazyLoadEvent } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { debounceTime, filter, map, Subscription } from 'rxjs';

import { AppError } from '../../../../core/errors/api-error';
import { toAppError } from '../../../../core/errors/api-error.util';
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

// Libellés français, valeurs = enums techniques du backend (AccountStatus /
// MemberStatus). `null` = aucun filtre (paramètre non envoyé).
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
  selector: 'app-staff-users-page',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    ButtonModule,
    InputTextModule,
    SelectModule,
    TableModule,
    TagModule,
    LoadingState,
    EmptyState,
    ErrorState,
  ],
  templateUrl: './staff-users-page.html',
  styleUrl: './staff-users-page.scss',
})
export class StaffUsersPage {
  private readonly userApiService = inject(UserApiService);
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly titleService = inject(Title);

  readonly accountStatusOptions = ACCOUNT_STATUS_FILTER_OPTIONS;
  readonly memberStatusOptions = MEMBER_STATUS_FILTER_OPTIONS;

  readonly filtersForm = this.formBuilder.group({
    q: this.formBuilder.control(''),
    accountStatus: this.formBuilder.control<AccountStatus | null>(null),
    memberStatus: this.formBuilder.control<MemberStatus | null>(null),
  });

  readonly rows = signal<UserResponse[]>([]);
  readonly totalRecords = signal(0);
  // Initialisé à true : le premier chargement est déclenché explicitement
  // ci-dessous (constructeur), jamais par le déclenchement automatique
  // PrimeNG au montage — le tableau n'est monté qu'une fois des données ou
  // une erreur disponibles (cf. template), il ne peut donc pas déclencher
  // lui-même ce premier appel.
  readonly loading = signal(true);
  readonly error = signal<AppError | null>(null);
  // Index du premier enregistrement affiché : lié au paginateur pour que
  // le retour à la page 0 après un changement de critère soit reflété dans
  // le composant (un `[first]="0"` constant ne se réappliquerait pas).
  readonly first = signal(0);
  // Critères réellement appliqués à la dernière requête (distincts de la
  // saisie en cours, non encore debouncée) : pilotent l'état vide.
  readonly filtersApplied = signal(false);

  private lastPage = 0;
  private lastSize = DEFAULT_PAGE_SIZE;
  // Dernier `q` (trimé) réellement envoyé : équivalent d'un
  // distinctUntilChanged qui reste correct après « Effacer ».
  private appliedQuery = '';
  private request: Subscription | null = null;

  constructor() {
    this.titleService.setTitle('Utilisateurs — PRIMATIS');

    this.filtersForm.controls.q.valueChanges
      .pipe(
        map((value) => value.trim()),
        debounceTime(SEARCH_DEBOUNCE_MS),
        filter((value) => value !== this.appliedQuery),
        takeUntilDestroyed(),
      )
      .subscribe(() => this.load(0, this.lastSize));

    this.filtersForm.controls.accountStatus.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.load(0, this.lastSize));
    this.filtersForm.controls.memberStatus.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.load(0, this.lastSize));

    this.load(0, DEFAULT_PAGE_SIZE);
  }

  hasActiveFilters(): boolean {
    const raw = this.filtersForm.getRawValue();
    return raw.q.trim().length > 0 || raw.accountStatus !== null || raw.memberStatus !== null;
  }

  /** Réinitialise les trois critères et recharge une seule fois, page 0. */
  clearFilters(): void {
    this.filtersForm.reset(
      { q: '', accountStatus: null, memberStatus: null },
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

  // Même précédent exact que MemberProfilePage.memberStatusLabel/
  
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

  private load(page: number, size: number): void {
    this.lastPage = page;
    this.lastSize = size;
    this.first.set(page * size);
    this.loading.set(true);
    this.error.set(null);

    const raw = this.filtersForm.getRawValue();
    const trimmedQ = raw.q.trim();
    this.appliedQuery = trimmedQ;
    this.filtersApplied.set(this.hasActiveFilters());

    // Une réponse tardive d'une requête devenue obsolète ne doit jamais
    // écraser le résultat de la requête la plus récente.
    this.request?.unsubscribe();
    this.request = this.userApiService
      .listUsers(
        page,
        size,
        trimmedQ || undefined,
        raw.accountStatus ?? undefined,
        raw.memberStatus ?? undefined,
      )
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
