import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { afterEach, vi } from 'vitest';

import { PageResponse } from '../../../../core/models/page-response';
import { UserResponse } from '../../../../user/models/user-response';
import { UserApiService } from '../../../../user/services/user-api.service';
import { StaffUsersPage } from './staff-users-page';

function buildUser(overrides: Partial<UserResponse> = {}): UserResponse {
  return {
    id: 1,
    email: 'librarian@primatis.test',
    firstName: 'Prénom',
    lastName: 'Nom',
    phoneNumber: null,
    accountStatus: 'ACTIVE',
    memberNumber: null,
    memberStatus: null,
    registrationDate: null,
    memberExpirationDate: null,
    blockedReason: null,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...overrides,
  };
}

function buildPage(content: UserResponse[], totalElements = content.length): PageResponse<UserResponse> {
  return { content, page: 0, size: 20, totalElements, totalPages: Math.max(1, Math.ceil(totalElements / 20)) };
}

function apiHttpError(code: string, message: string): HttpErrorResponse {
  return new HttpErrorResponse({
    status: 500,
    error: {
      timestamp: new Date().toISOString(),
      status: 500,
      error: 'Internal Server Error',
      code,
      message,
      path: '/api/v1/users',
      fieldErrors: [],
    },
  });
}

describe('StaffUsersPage', () => {
  let fixture: ComponentFixture<StaffUsersPage>;
  let userApiServiceMock: { listUsers: ReturnType<typeof vi.fn> };

  function configure(): void {
    userApiServiceMock = { listUsers: vi.fn() };

    TestBed.configureTestingModule({
      imports: [StaffUsersPage],
      providers: [provideRouter([]), { provide: UserApiService, useValue: userApiServiceMock }],
    });
  }

  function createComponent(): void {
    fixture = TestBed.createComponent(StaffUsersPage);
    fixture.detectChanges();
  }

  it('should call listUsers(0, 20) on initial load', () => {
    configure();
    userApiServiceMock.listUsers.mockReturnValue(of(buildPage([buildUser()])));

    createComponent();

    expect(userApiServiceMock.listUsers).toHaveBeenCalledWith(0, 20, undefined, undefined, undefined);
  });

  it('should render the expected columns for a user', () => {
    configure();
    userApiServiceMock.listUsers.mockReturnValue(
      of(
        buildPage([
          buildUser({
            firstName: 'Marie',
            lastName: 'Curie',
            email: 'marie@primatis.test',
            memberNumber: 'M000000001',
            memberStatus: 'ACTIVE',
          }),
        ]),
      ),
    );

    createComponent();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Marie Curie');
    expect(text).toContain('marie@primatis.test');
    expect(text).toContain('M000000001');
    expect(text).toContain('Actif');
  });

  it('should set the document title (DEV-15.8)', () => {
    configure();
    userApiServiceMock.listUsers.mockReturnValue(of(buildPage([buildUser()])));

    createComponent();

    expect(document.title).toBe('Utilisateurs — PRIMATIS');
  });

  it('should render an explicit non-member label for a null memberStatus', () => {
    configure();
    userApiServiceMock.listUsers.mockReturnValue(
      of(buildPage([buildUser({ memberNumber: null, memberStatus: null })])),
    );

    createComponent();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('—');
    expect(text).toContain('Non adhérent');
  });

  it('should render the VISUAL-RESET-17 hero and user register hierarchy', () => {
    configure();
    userApiServiceMock.listUsers.mockReturnValue(of(buildPage([buildUser()], 23)));

    createComponent();

    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('.staff-users-hero')).not.toBeNull();
    expect(root.querySelector('.staff-users-register')).not.toBeNull();
    expect(root.querySelector('#staff-users-register-title')?.textContent).toContain('Comptes enregistrés');
    expect(root.textContent).toContain('23 utilisateurs');
  });

  it('should expose data labels required by the responsive user cards', () => {
    configure();
    userApiServiceMock.listUsers.mockReturnValue(of(buildPage([buildUser()])));

    createComponent();

    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('td[data-label="Utilisateur"]')).not.toBeNull();
    expect(root.querySelector('td[data-label="N° adhérent"]')).not.toBeNull();
    expect(root.querySelector('td[data-label="Compte"]')).not.toBeNull();
    expect(root.querySelector('td[data-label="Adhésion"]')).not.toBeNull();
    expect(root.querySelector('td[data-label="Email"]')).not.toBeNull();
    expect(root.querySelector('td[data-label="Action"]')).not.toBeNull();
  });

  it('should map a PrimeNG lazy load event to page/size and call listUsers again', () => {
    configure();
    userApiServiceMock.listUsers.mockReturnValue(of(buildPage([buildUser()], 100)));
    createComponent();
    userApiServiceMock.listUsers.mockClear();

    fixture.componentInstance.onLazyLoad({ first: 40, rows: 20 });

    expect(userApiServiceMock.listUsers).toHaveBeenCalledWith(2, 20, undefined, undefined, undefined);
  });

  it('should default to page 0 / size 20 when the lazy load event omits first/rows', () => {
    configure();
    userApiServiceMock.listUsers.mockReturnValue(of(buildPage([buildUser()])));
    createComponent();
    userApiServiceMock.listUsers.mockClear();

    fixture.componentInstance.onLazyLoad({});

    expect(userApiServiceMock.listUsers).toHaveBeenCalledWith(0, 20, undefined, undefined, undefined);
  });

  it('should show the loading state before the first response arrives', () => {
    configure();
    // Observable never emits synchronously: keeps the component in its initial loading state.
    userApiServiceMock.listUsers.mockReturnValue({ subscribe: () => ({ unsubscribe: () => {} }) });

    createComponent();

    expect(fixture.nativeElement.querySelector('app-loading-state')).not.toBeNull();
  });

  it('should show the empty state when the backend returns no user', () => {
    configure();
    userApiServiceMock.listUsers.mockReturnValue(of(buildPage([], 0)));

    createComponent();

    expect(fixture.nativeElement.querySelector('app-empty-state')).not.toBeNull();
  });

  it('should show the error state on a failed request', () => {
    configure();
    userApiServiceMock.listUsers.mockReturnValue(throwError(() => apiHttpError('INTERNAL_ERROR', 'Erreur serveur.')));

    createComponent();

    expect(fixture.nativeElement.querySelector('app-error-state')).not.toBeNull();
  });

  it('should retry the last request when retry is triggered', () => {
    configure();
    userApiServiceMock.listUsers.mockReturnValue(throwError(() => apiHttpError('INTERNAL_ERROR', 'Erreur serveur.')));
    createComponent();
    userApiServiceMock.listUsers.mockClear();
    userApiServiceMock.listUsers.mockReturnValue(of(buildPage([buildUser()])));

    fixture.componentInstance.retry();

    expect(userApiServiceMock.listUsers).toHaveBeenCalledWith(0, 20, undefined, undefined, undefined);
  });

  it('should link each row to its detail page', () => {
    configure();
    userApiServiceMock.listUsers.mockReturnValue(of(buildPage([buildUser({ id: 42 })])));

    createComponent();

    expect(fixture.nativeElement.querySelector('a[href="/staff/users/42"]')).not.toBeNull();
  });

  
  
  

  describe('search and filters', () => {
    let component: StaffUsersPage;

    function lastCall(): unknown[] {
      return userApiServiceMock.listUsers.mock.calls.at(-1) as unknown[];
    }

    beforeEach(() => {
      vi.useFakeTimers();
      configure();
      userApiServiceMock.listUsers.mockReturnValue(of(buildPage([buildUser()], 100)));
      createComponent();
      component = fixture.componentInstance;
      userApiServiceMock.listUsers.mockClear();
    });

    afterEach(() => vi.useRealTimers());

    it('should render labelled search and status controls with French labels', () => {
      const root = fixture.nativeElement as HTMLElement;

      expect(root.querySelector('label[for="staff-users-search"]')?.textContent).toContain(
        'Rechercher un utilisateur',
      );
      expect(root.querySelector('label[for="staff-users-account-status"]')?.textContent).toContain(
        'Statut du compte',
      );
      expect(root.querySelector('label[for="staff-users-member-status"]')?.textContent).toContain(
        'Statut d’adhésion',
      );
      expect(component.accountStatusOptions.map((o) => o.label)).toEqual([
        'Tous les comptes',
        'Actifs',
        'Désactivés',
      ]);
      expect(component.memberStatusOptions.map((o) => o.label)).toEqual([
        'Toutes les adhésions',
        'Actives',
        'Bloquées',
        'Expirées',
      ]);
    });

    it('should send technical enum values, not French labels', () => {
      expect(component.accountStatusOptions.map((o) => o.value)).toEqual([null, 'ACTIVE', 'DISABLED']);
      expect(component.memberStatusOptions.map((o) => o.value)).toEqual([
        null,
        'ACTIVE',
        'BLOCKED',
        'EXPIRED',
      ]);
    });

    it('should debounce q, trim it and reset to page 0', () => {
      component.onLazyLoad({ first: 40, rows: 20 });
      userApiServiceMock.listUsers.mockClear();

      component.filtersForm.controls.q.setValue('  martin ');
      vi.advanceTimersByTime(299);
      expect(userApiServiceMock.listUsers).not.toHaveBeenCalled();

      vi.advanceTimersByTime(1);
      expect(userApiServiceMock.listUsers).toHaveBeenCalledTimes(1);
      expect(lastCall()).toEqual([0, 20, 'martin', undefined, undefined]);
    });

    it('should send a single request for a burst of keystrokes', () => {
      component.filtersForm.controls.q.setValue('m');
      vi.advanceTimersByTime(100);
      component.filtersForm.controls.q.setValue('ma');
      vi.advanceTimersByTime(100);
      component.filtersForm.controls.q.setValue('mar');
      vi.advanceTimersByTime(300);

      expect(userApiServiceMock.listUsers).toHaveBeenCalledTimes(1);
      expect(lastCall()).toEqual([0, 20, 'mar', undefined, undefined]);
    });

    it('should not reload when the trimmed q is unchanged', () => {
      component.filtersForm.controls.q.setValue('martin');
      vi.advanceTimersByTime(300);
      userApiServiceMock.listUsers.mockClear();

      component.filtersForm.controls.q.setValue('martin  ');
      vi.advanceTimersByTime(300);

      expect(userApiServiceMock.listUsers).not.toHaveBeenCalled();
    });

    it('should send accountStatus immediately and reset to page 0', () => {
      component.onLazyLoad({ first: 40, rows: 20 });
      userApiServiceMock.listUsers.mockClear();

      component.filtersForm.controls.accountStatus.setValue('DISABLED');

      expect(lastCall()).toEqual([0, 20, undefined, 'DISABLED', undefined]);
      expect(component.first()).toBe(0);
    });

    it('should send memberStatus immediately and reset to page 0', () => {
      component.onLazyLoad({ first: 40, rows: 20 });
      userApiServiceMock.listUsers.mockClear();

      component.filtersForm.controls.memberStatus.setValue('BLOCKED');

      expect(lastCall()).toEqual([0, 20, undefined, undefined, 'BLOCKED']);
    });

    it('should combine q and both filters', () => {
      component.filtersForm.controls.accountStatus.setValue('ACTIVE');
      component.filtersForm.controls.memberStatus.setValue('EXPIRED');
      component.filtersForm.controls.q.setValue('dupont');
      vi.advanceTimersByTime(300);

      expect(lastCall()).toEqual([0, 20, 'dupont', 'ACTIVE', 'EXPIRED']);
    });

    it('should keep every criterion when the page changes', () => {
      component.filtersForm.controls.accountStatus.setValue('ACTIVE');
      component.filtersForm.controls.memberStatus.setValue('EXPIRED');
      component.filtersForm.controls.q.setValue('dupont');
      vi.advanceTimersByTime(300);
      userApiServiceMock.listUsers.mockClear();

      component.onLazyLoad({ first: 20, rows: 20 });

      expect(lastCall()).toEqual([1, 20, 'dupont', 'ACTIVE', 'EXPIRED']);
      expect(component.first()).toBe(20);
    });

    it('should reload with the current criteria on retry', () => {
      component.filtersForm.controls.memberStatus.setValue('BLOCKED');
      userApiServiceMock.listUsers.mockClear();

      component.retry();

      expect(lastCall()).toEqual([0, 20, undefined, undefined, 'BLOCKED']);
    });

    it('should show the clear button only when a criterion is active', () => {
      const root = fixture.nativeElement as HTMLElement;
      expect(root.querySelector('p-button[label="Effacer"]')).toBeNull();

      component.filtersForm.controls.accountStatus.setValue('DISABLED');
      fixture.detectChanges();

      expect(root.querySelector('p-button[label="Effacer"]')).not.toBeNull();
    });

    it('should clear every criterion with a single request on page 0', () => {
      component.filtersForm.controls.accountStatus.setValue('DISABLED');
      component.filtersForm.controls.memberStatus.setValue('BLOCKED');
      component.filtersForm.controls.q.setValue('martin');
      vi.advanceTimersByTime(300);
      component.onLazyLoad({ first: 20, rows: 20 });
      userApiServiceMock.listUsers.mockClear();

      component.clearFilters();
      vi.advanceTimersByTime(1000);

      expect(userApiServiceMock.listUsers).toHaveBeenCalledTimes(1);
      expect(lastCall()).toEqual([0, 20, undefined, undefined, undefined]);
      expect(component.filtersForm.getRawValue()).toEqual({
        q: '',
        accountStatus: null,
        memberStatus: null,
      });
      expect(component.hasActiveFilters()).toBe(false);
    });

    it('should search again for the same q after clearing', () => {
      component.filtersForm.controls.q.setValue('martin');
      vi.advanceTimersByTime(300);
      component.clearFilters();
      userApiServiceMock.listUsers.mockClear();

      component.filtersForm.controls.q.setValue('martin');
      vi.advanceTimersByTime(300);

      expect(lastCall()).toEqual([0, 20, 'martin', undefined, undefined]);
    });

    it('should show the search-specific empty state when filters match nothing', () => {
      userApiServiceMock.listUsers.mockReturnValue(of(buildPage([], 0)));

      component.filtersForm.controls.memberStatus.setValue('EXPIRED');
      fixture.detectChanges();

      const root = fixture.nativeElement as HTMLElement;
      expect(root.querySelector('app-empty-state')?.textContent).toContain(
        'Aucun utilisateur ne correspond à votre recherche.',
      );
      // Les critères restent visibles pour pouvoir les modifier ou les effacer.
      expect(root.querySelector('#staff-users-search')).not.toBeNull();
      expect(root.querySelector('p-button[label="Effacer"]')).not.toBeNull();
    });

    it('should ignore a stale response arriving after a newer request', () => {
      const first$ = new Subject<PageResponse<UserResponse>>();
      const second$ = new Subject<PageResponse<UserResponse>>();
      userApiServiceMock.listUsers.mockReturnValueOnce(first$).mockReturnValueOnce(second$);

      component.filtersForm.controls.accountStatus.setValue('ACTIVE');
      component.filtersForm.controls.memberStatus.setValue('BLOCKED');
      second$.next(buildPage([buildUser({ id: 2 })], 1));
      first$.next(buildPage([buildUser({ id: 1 }), buildUser({ id: 3 })], 2));

      expect(component.rows().map((u) => u.id)).toEqual([2]);
      expect(component.totalRecords()).toBe(1);
    });
  });
});
