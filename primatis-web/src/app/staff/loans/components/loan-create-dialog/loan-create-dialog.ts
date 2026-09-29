import { Component, effect, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { TagModule } from 'primeng/tag';
import { Subject, debounceTime } from 'rxjs';

import { AvailabilityStatus } from '../../../../catalogue/models/availability-status';
import { CopyCondition } from '../../../../catalogue/models/copy-condition';
import { CopyResponse } from '../../../../catalogue/models/copy-response';
import { TitleResponse } from '../../../../catalogue/models/title-response';
import { CopyApiService } from '../../../../catalogue/services/copy-api.service';
import { StaffCatalogueApiService } from '../../../../catalogue/services/staff-catalogue-api.service';
import { AppError } from '../../../../core/errors/api-error';
import { toAppError } from '../../../../core/errors/api-error.util';
import { CreateLoanRequest } from '../../../../loans/models/create-loan-request';
import { LoanResponse } from '../../../../loans/models/loan-response';
import { LoanApiService } from '../../../../loans/services/loan-api.service';
import {
  copyAvailabilityStatusSeverity,
  copyConditionSeverity as sharedCopyConditionSeverity,
  StatusTagSeverity,
} from '../../../../shared/status/status-severity';
import { EmptyState } from '../../../../shared/ui/empty-state/empty-state';
import { ErrorState } from '../../../../shared/ui/error-state/error-state';
import { LoadingState } from '../../../../shared/ui/loading-state/loading-state';
import { UserResponse } from '../../../../user/models/user-response';
import { UserApiService } from '../../../../user/services/user-api.service';

const SEARCH_DEBOUNCE_MS = 300;
const BORROWER_SEARCH_PAGE_SIZE = 20;
const TITLE_SEARCH_PAGE_SIZE = 20;


@Component({
  selector: 'app-loan-create-dialog',
  imports: [
    FormsModule,
    ButtonModule,
    DialogModule,
    InputTextModule,
    MessageModule,
    TagModule,
    LoadingState,
    EmptyState,
    ErrorState,
  ],
  templateUrl: './loan-create-dialog.html',
  styleUrl: './loan-create-dialog.scss',
})
export class LoanCreateDialog {
  private readonly userApiService = inject(UserApiService);
  private readonly staffCatalogueApiService = inject(StaffCatalogueApiService);
  private readonly copyApiService = inject(CopyApiService);
  private readonly loanApiService = inject(LoanApiService);
  private readonly messageService = inject(MessageService);

  readonly visible = input.required<boolean>();

  readonly closed = output<void>();
  readonly saved = output<LoanResponse>();

  readonly borrowerSearchTerm = signal('');
  readonly borrowerSearchResults = signal<UserResponse[]>([]);
  readonly borrowerSearching = signal(false);
  readonly borrowerSearchError = signal<AppError | null>(null);
  readonly selectedBorrower = signal<UserResponse | null>(null);

  readonly titleSearchTerm = signal('');
  readonly titleSearchResults = signal<TitleResponse[]>([]);
  readonly titleSearching = signal(false);
  readonly titleSearchError = signal<AppError | null>(null);
  readonly selectedTitle = signal<TitleResponse | null>(null);

  readonly copies = signal<CopyResponse[]>([]);
  readonly copiesLoading = signal(false);
  readonly copiesError = signal<AppError | null>(null);
  readonly selectedCopy = signal<CopyResponse | null>(null);

  readonly submitting = signal(false);
  readonly submitError = signal<string | null>(null);

  private readonly borrowerSearchInput$ = new Subject<string>();
  private readonly titleSearchInput$ = new Subject<string>();

  constructor() {
    
    // vérification manuelle) : ce Subject est créé une seule fois pour toute
    // la durée de vie du dialog (jamais recréé à la fermeture/réouverture,
    // seul l'état des Signals est réinitialisé via `resetState()`) — son
    // dernier terme mémorisé aurait donc silencieusement supprimé une
    // recherche identique à une session précédente (ex. rechercher deux
    // fois le même Title pour deux emprunteurs différents), affichant à tort
    // "Aucun titre trouvé". `debounceTime` seul suffit à limiter les appels
    // HTTP pendant la frappe.
    this.borrowerSearchInput$
      .pipe(debounceTime(SEARCH_DEBOUNCE_MS), takeUntilDestroyed())
      .subscribe((value) => this.runBorrowerSearch(value));

    this.titleSearchInput$
      .pipe(debounceTime(SEARCH_DEBOUNCE_MS), takeUntilDestroyed())
      .subscribe((value) => this.runTitleSearch(value));

    // Réinitialise systématiquement à chaque (ré)ouverture : jamais de
    // sélection obsolète d'une session précédente (même principe que
    // AuthorFormDialog).
    effect(() => {
      if (this.visible()) {
        this.resetState();
      }
    });
  }

  get canSubmit(): boolean {
    return this.selectedBorrower() !== null && this.selectedCopy() !== null && !this.submitting();
  }

  onBorrowerSearchInput(value: string): void {
    this.borrowerSearchTerm.set(value);
    this.borrowerSearchInput$.next(value);
  }

  /**
   * Un résultat sans `memberNumber` n'est jamais présenté comme un
   * emprunteur valide : `GET /api/v1/users?q=` est générique et peut
   * retourner des non-adhérents.
   */
  selectBorrower(borrower: UserResponse): void {
    if (borrower.memberNumber === null) {
      return;
    }
    this.selectedBorrower.set(borrower);
  }

  changeBorrower(): void {
    this.selectedBorrower.set(null);
    this.borrowerSearchTerm.set('');
    this.borrowerSearchResults.set([]);
  }

  onTitleSearchInput(value: string): void {
    this.titleSearchTerm.set(value);
    this.titleSearchInput$.next(value);
  }

  selectTitle(title: TitleResponse): void {
    this.selectedTitle.set(title);
    this.selectedCopy.set(null);
    this.loadCopies(title.id);
  }

  changeTitle(): void {
    this.selectedTitle.set(null);
    this.selectedCopy.set(null);
    this.titleSearchTerm.set('');
    this.titleSearchResults.set([]);
    this.copies.set([]);
    this.copiesError.set(null);
  }

  selectCopy(copy: CopyResponse): void {
    this.selectedCopy.set(copy);
  }

  
  copyAvailabilitySeverity(status: AvailabilityStatus): StatusTagSeverity {
    return copyAvailabilityStatusSeverity(status);
  }

  copyConditionSeverity(condition: CopyCondition): StatusTagSeverity {
    return sharedCopyConditionSeverity(condition);
  }

  retryCopies(): void {
    const title = this.selectedTitle();
    if (title !== null) {
      this.loadCopies(title.id);
    }
  }

  cancel(): void {
    this.closed.emit();
  }

  submit(): void {
    if (this.submitting()) {
      return;
    }
    const borrower = this.selectedBorrower();
    const copy = this.selectedCopy();
    if (borrower === null || copy === null) {
      return;
    }

    this.submitting.set(true);
    this.submitError.set(null);

    const request: CreateLoanRequest = { borrowerUserId: borrower.id, copyId: copy.id };
    this.loanApiService.registerLoan(request).subscribe({
      next: (loan) => {
        this.messageService.add({
          severity: 'success',
          summary: 'Prêt enregistré',
          detail: `Le prêt de l'exemplaire ${loan.copy.inventoryCode} a été enregistré.`,
        });
        this.saved.emit(loan);
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

  private runBorrowerSearch(value: string): void {
    const trimmed = value.trim();
    if (trimmed.length === 0) {
      this.borrowerSearchResults.set([]);
      this.borrowerSearching.set(false);
      this.borrowerSearchError.set(null);
      return;
    }
    this.borrowerSearching.set(true);
    this.borrowerSearchError.set(null);
    this.userApiService.listUsers(0, BORROWER_SEARCH_PAGE_SIZE, trimmed).subscribe({
      next: (response) => {
        this.borrowerSearchResults.set(response.content);
        this.borrowerSearching.set(false);
      },
      error: (err: unknown) => {
        this.borrowerSearching.set(false);
        this.borrowerSearchError.set(toAppError(err));
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

  private loadCopies(titleId: number): void {
    this.copiesLoading.set(true);
    this.copiesError.set(null);
    this.copyApiService.listCopies(titleId).subscribe({
      next: (copies) => {
        this.copies.set(copies);
        this.copiesLoading.set(false);
      },
      error: (err: unknown) => {
        this.copiesLoading.set(false);
        this.copiesError.set(toAppError(err));
      },
    });
  }

  private resetState(): void {
    this.borrowerSearchTerm.set('');
    this.borrowerSearchResults.set([]);
    this.borrowerSearching.set(false);
    this.borrowerSearchError.set(null);
    this.selectedBorrower.set(null);

    this.titleSearchTerm.set('');
    this.titleSearchResults.set([]);
    this.titleSearching.set(false);
    this.titleSearchError.set(null);
    this.selectedTitle.set(null);

    this.copies.set([]);
    this.copiesLoading.set(false);
    this.copiesError.set(null);
    this.selectedCopy.set(null);

    this.submitting.set(false);
    this.submitError.set(null);
  }
}
