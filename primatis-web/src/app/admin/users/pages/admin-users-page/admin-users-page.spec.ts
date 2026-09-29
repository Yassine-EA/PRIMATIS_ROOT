import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { vi } from 'vitest';

import { isoDateToDate } from '../../../../shared/date/iso-date';
import { PageResponse } from '../../../../core/models/page-response';
import { UserResponse } from '../../../../user/models/user-response';
import { UserApiService } from '../../../../user/services/user-api.service';
import { AdminUsersPage } from './admin-users-page';

function buildUser(overrides: Partial<UserResponse> = {}): UserResponse {
  return {
    id: 1,
    email: 'admin-list@primatis.test',
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

describe('AdminUsersPage', () => {
  let fixture: ComponentFixture<AdminUsersPage>;
  let userApiServiceMock: { listUsers: ReturnType<typeof vi.fn> };

  function configure(): void {
    userApiServiceMock = { listUsers: vi.fn() };

    TestBed.configureTestingModule({
      imports: [AdminUsersPage],
      providers: [provideRouter([]), { provide: UserApiService, useValue: userApiServiceMock }],
    });
  }

  function createComponent(): void {
    fixture = TestBed.createComponent(AdminUsersPage);
    fixture.detectChanges();
  }

  it('should call listUsers(0, 20) on initial load', () => {
    configure();
    userApiServiceMock.listUsers.mockReturnValue(of(buildPage([buildUser()])));

    createComponent();

    expect(userApiServiceMock.listUsers).toHaveBeenCalledWith(0, 20, undefined, undefined, undefined, {});
  });

  it('should render the expected columns for a user, without a Roles column', () => {
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

    const headerText = Array.from(fixture.nativeElement.querySelectorAll('th')).map(
      (th) => (th as HTMLElement).textContent?.trim() ?? '',
    );
    expect(headerText).not.toContain('Roles');
    expect(headerText).not.toContain('Rôles');

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Marie Curie');
    expect(text).toContain('marie@primatis.test');
    expect(text).toContain('M000000001');
    expect(text).toContain('Actif');
  });

  it('should render — for a null memberNumber and a null memberStatus', () => {
    configure();
    userApiServiceMock.listUsers.mockReturnValue(
      of(buildPage([buildUser({ memberNumber: null, memberStatus: null })])),
    );

    createComponent();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('—');
  });

  it('should map a PrimeNG lazy load event to page/size and call listUsers again', () => {
    configure();
    userApiServiceMock.listUsers.mockReturnValue(of(buildPage([buildUser()], 100)));
    createComponent();
    userApiServiceMock.listUsers.mockClear();

    fixture.componentInstance.onLazyLoad({ first: 40, rows: 20 });

    expect(userApiServiceMock.listUsers).toHaveBeenCalledWith(2, 20, undefined, undefined, undefined, {});
  });

  it('should default to page 0 / size 20 when the lazy load event omits first/rows', () => {
    configure();
    userApiServiceMock.listUsers.mockReturnValue(of(buildPage([buildUser()])));
    createComponent();
    userApiServiceMock.listUsers.mockClear();

    fixture.componentInstance.onLazyLoad({});

    expect(userApiServiceMock.listUsers).toHaveBeenCalledWith(0, 20, undefined, undefined, undefined, {});
  });

  it('should show the loading state before the first response arrives', () => {
    configure();
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

    expect(userApiServiceMock.listUsers).toHaveBeenCalledWith(0, 20, undefined, undefined, undefined, {});
  });

  it('should link each row to its admin detail page', () => {
    configure();
    userApiServiceMock.listUsers.mockReturnValue(of(buildPage([buildUser({ id: 42 })])));

    createComponent();

    expect(fixture.nativeElement.querySelector('a[href="/admin/users/42"]')).not.toBeNull();
  });

  it('should link to /admin/users/new to create a user', () => {
    configure();
    userApiServiceMock.listUsers.mockReturnValue(of(buildPage([buildUser()])));

    createComponent();

    expect(fixture.nativeElement.querySelector('a[href="/admin/users/new"]')).not.toBeNull();
  });

  it('should not expose column filters or sort controls (filters live in the search form)', () => {
    configure();
    userApiServiceMock.listUsers.mockReturnValue(of(buildPage([buildUser()])));

    createComponent();

    expect(fixture.nativeElement.querySelector('p-columnfilter')).toBeNull();
    expect(fixture.nativeElement.querySelector('p-sorticon')).toBeNull();
  });

  
  
  

  describe('search and filters', () => {
    let component: AdminUsersPage;

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

    it('should render labelled controls, fieldsets and French labels', () => {
      const root = fixture.nativeElement as HTMLElement;

      expect(root.querySelector('label[for="admin-users-search"]')?.textContent).toContain(
        'Rechercher un utilisateur',
      );
      expect(root.querySelector<HTMLInputElement>('#admin-users-search')?.placeholder).toBe(
        'Rechercher par nom, prénom, email ou n° adhérent…',
      );
      expect(root.querySelector('label[for="admin-users-account-status"]')?.textContent).toContain(
        'Statut du compte',
      );
      expect(root.querySelector('label[for="admin-users-member-status"]')?.textContent).toContain(
        'Statut de l’adhésion',
      );
      expect(Array.from(root.querySelectorAll('legend')).map((l) => l.textContent?.trim())).toEqual([
        'Création du compte',
        'Expiration de l’adhésion',
      ]);
      expect(root.querySelectorAll('fieldset').length).toBe(2);
      expect(root.querySelector<HTMLInputElement>('input#admin-users-created-from')?.placeholder).toBe('jj/mm/aaaa');
      expect(root.querySelector<HTMLInputElement>('input#admin-users-expiration-to')?.placeholder).toBe('jj/mm/aaaa');
      expect(root.querySelector('input[type="date"]')).toBeNull();
      expect(component.accountStatusOptions.map((o) => o.label)).toEqual(['Tous les comptes', 'Actifs', 'Désactivés']);
      expect(component.accountStatusOptions.map((o) => o.value)).toEqual([null, 'ACTIVE', 'DISABLED']);
      expect(component.memberStatusOptions.map((o) => o.label)).toEqual([
        'Toutes les adhésions',
        'Actives',
        'Bloquées',
        'Expirées',
      ]);
      expect(component.memberStatusOptions.map((o) => o.value)).toEqual([null, 'ACTIVE', 'BLOCKED', 'EXPIRED']);
    });

    it('should debounce q, trim it and reset to page 0', () => {
      component.onLazyLoad({ first: 40, rows: 20 });
      userApiServiceMock.listUsers.mockClear();

      component.filtersForm.controls.q.setValue('  martin ');
      vi.advanceTimersByTime(299);
      expect(userApiServiceMock.listUsers).not.toHaveBeenCalled();

      vi.advanceTimersByTime(1);
      expect(userApiServiceMock.listUsers).toHaveBeenCalledTimes(1);
      expect(lastCall()).toEqual([0, 20, 'martin', undefined, undefined, {}]);
      expect(component.first()).toBe(0);
    });

    it('should send one request for a burst of keystrokes and none for an unchanged trimmed q', () => {
      component.filtersForm.controls.q.setValue('m');
      vi.advanceTimersByTime(100);
      component.filtersForm.controls.q.setValue('mar');
      vi.advanceTimersByTime(300);
      expect(userApiServiceMock.listUsers).toHaveBeenCalledTimes(1);

      component.filtersForm.controls.q.setValue('mar  ');
      vi.advanceTimersByTime(300);
      expect(userApiServiceMock.listUsers).toHaveBeenCalledTimes(1);
    });

    it('should send accountStatus and memberStatus immediately and reset to page 0', () => {
      component.onLazyLoad({ first: 40, rows: 20 });
      userApiServiceMock.listUsers.mockClear();

      component.filtersForm.controls.accountStatus.setValue('DISABLED');
      expect(lastCall()).toEqual([0, 20, undefined, 'DISABLED', undefined, {}]);
      expect(component.first()).toBe(0);

      component.filtersForm.controls.memberStatus.setValue('EXPIRED');
      expect(lastCall()).toEqual([0, 20, undefined, 'DISABLED', 'EXPIRED', {}]);
      expect(userApiServiceMock.listUsers).toHaveBeenCalledTimes(2);
    });

    it('should send the creation and expiration ranges as ISO days', () => {
      component.filtersForm.controls.createdDateFrom.setValue(isoDateToDate('2026-03-01'));
      component.filtersForm.controls.createdDateTo.setValue(new Date(2026, 2, 31, 23, 59, 59));
      component.filtersForm.controls.memberExpirationDateFrom.setValue(isoDateToDate('2027-01-01'));
      component.filtersForm.controls.memberExpirationDateTo.setValue(isoDateToDate('2027-12-31'));

      expect(lastCall()).toEqual([
        0,
        20,
        undefined,
        undefined,
        undefined,
        {
          createdDateFrom: '2026-03-01',
          createdDateTo: '2026-03-31',
          memberExpirationDateFrom: '2027-01-01',
          memberExpirationDateTo: '2027-12-31',
        },
      ]);
    });

    it('should accept open-ended and same-day ranges', () => {
      component.filtersForm.controls.createdDateTo.setValue(isoDateToDate('2026-03-15'));
      expect(lastCall()).toEqual([0, 20, undefined, undefined, undefined, { createdDateTo: '2026-03-15' }]);

      component.filtersForm.controls.createdDateFrom.setValue(isoDateToDate('2026-03-15'));
      expect(lastCall()[5]).toEqual({ createdDateFrom: '2026-03-15', createdDateTo: '2026-03-15' });
    });

    it('should not reload when a datepicker re-emits an unchanged empty value (partial keystrokes)', () => {
      for (let i = 0; i < 5; i++) {
        component.filtersForm.controls.createdDateTo.setValue(null);
        component.filtersForm.controls.memberExpirationDateFrom.setValue(null);
      }

      expect(userApiServiceMock.listUsers).not.toHaveBeenCalled();
    });

    it('should reload once for a date typed after several empty re-emissions', () => {
      component.filtersForm.controls.memberExpirationDateTo.setValue(null);
      component.filtersForm.controls.memberExpirationDateTo.setValue(null);
      component.filtersForm.controls.memberExpirationDateTo.setValue(isoDateToDate('2027-05-10'));
      component.filtersForm.controls.memberExpirationDateTo.setValue(isoDateToDate('2027-05-10'));

      expect(userApiServiceMock.listUsers).toHaveBeenCalledTimes(1);
    });

    it('should combine q, both statuses and both ranges', () => {
      component.filtersForm.controls.accountStatus.setValue('ACTIVE');
      component.filtersForm.controls.memberStatus.setValue('ACTIVE');
      component.filtersForm.controls.createdDateFrom.setValue(isoDateToDate('2026-01-01'));
      component.filtersForm.controls.memberExpirationDateTo.setValue(isoDateToDate('2027-12-31'));
      component.filtersForm.controls.q.setValue('martin');
      vi.advanceTimersByTime(300);

      expect(lastCall()).toEqual([
        0,
        20,
        'martin',
        'ACTIVE',
        'ACTIVE',
        { createdDateFrom: '2026-01-01', memberExpirationDateTo: '2027-12-31' },
      ]);
    });

    it('should keep every criterion when the page changes', () => {
      component.filtersForm.controls.memberStatus.setValue('BLOCKED');
      component.filtersForm.controls.createdDateFrom.setValue(isoDateToDate('2026-01-01'));
      userApiServiceMock.listUsers.mockClear();

      component.onLazyLoad({ first: 20, rows: 20 });

      expect(lastCall()).toEqual([1, 20, undefined, undefined, 'BLOCKED', { createdDateFrom: '2026-01-01' }]);
      expect(component.first()).toBe(20);
    });

    it('should not call the API and should show the range message for an inverted creation range', () => {
      component.filtersForm.controls.createdDateFrom.setValue(isoDateToDate('2026-03-10'));
      userApiServiceMock.listUsers.mockClear();

      component.filtersForm.controls.createdDateTo.setValue(isoDateToDate('2026-03-01'));
      fixture.detectChanges();

      expect(userApiServiceMock.listUsers).not.toHaveBeenCalled();
      const root = fixture.nativeElement as HTMLElement;
      expect(root.querySelector('#admin-users-created-range-error')?.textContent).toContain(
        'La date de début doit précéder la date de fin.',
      );
      expect(root.querySelector('#admin-users-created-range-error')?.getAttribute('role')).toBe('alert');
      expect(root.querySelector('#admin-users-expiration-range-error')).toBeNull();
    });

    it('should not call the API and should show the range message for an inverted expiration range', () => {
      component.filtersForm.controls.memberExpirationDateFrom.setValue(isoDateToDate('2027-03-10'));
      userApiServiceMock.listUsers.mockClear();

      component.filtersForm.controls.memberExpirationDateTo.setValue(isoDateToDate('2027-03-01'));
      fixture.detectChanges();

      expect(userApiServiceMock.listUsers).not.toHaveBeenCalled();
      expect(
        (fixture.nativeElement as HTMLElement).querySelector('#admin-users-expiration-range-error'),
      ).not.toBeNull();
    });

    it('should search again once an inverted range is corrected', () => {
      component.filtersForm.controls.memberExpirationDateFrom.setValue(isoDateToDate('2027-03-10'));
      component.filtersForm.controls.memberExpirationDateTo.setValue(isoDateToDate('2027-03-01'));
      userApiServiceMock.listUsers.mockClear();

      component.filtersForm.controls.memberExpirationDateTo.setValue(isoDateToDate('2027-03-30'));

      expect(lastCall()).toEqual([
        0,
        20,
        undefined,
        undefined,
        undefined,
        { memberExpirationDateFrom: '2027-03-10', memberExpirationDateTo: '2027-03-30' },
      ]);
    });

    it('should show the clear button only when a criterion is active', () => {
      const root = fixture.nativeElement as HTMLElement;
      expect(root.querySelector('p-button[label="Effacer"]')).toBeNull();

      component.filtersForm.controls.accountStatus.setValue('DISABLED');
      fixture.detectChanges();

      expect(root.querySelector('p-button[label="Effacer"]')).not.toBeNull();
    });

    it('should clear every criterion with a single request on page 0', () => {
      component.filtersForm.controls.accountStatus.setValue('ACTIVE');
      component.filtersForm.controls.memberStatus.setValue('EXPIRED');
      component.filtersForm.controls.createdDateFrom.setValue(isoDateToDate('2026-01-01'));
      component.filtersForm.controls.memberExpirationDateTo.setValue(isoDateToDate('2027-12-31'));
      component.filtersForm.controls.q.setValue('martin');
      vi.advanceTimersByTime(300);
      component.onLazyLoad({ first: 20, rows: 20 });
      userApiServiceMock.listUsers.mockClear();

      component.clearFilters();
      vi.advanceTimersByTime(1000);

      expect(userApiServiceMock.listUsers).toHaveBeenCalledTimes(1);
      expect(lastCall()).toEqual([0, 20, undefined, undefined, undefined, {}]);
      expect(component.filtersForm.getRawValue()).toEqual({
        q: '',
        accountStatus: null,
        memberStatus: null,
        createdDateFrom: null,
        createdDateTo: null,
        memberExpirationDateFrom: null,
        memberExpirationDateTo: null,
      });
      expect(component.hasActiveFilters()).toBe(false);
      expect(component.first()).toBe(0);
    });

    it('should search again with the same q after clearing', () => {
      component.filtersForm.controls.q.setValue('martin');
      vi.advanceTimersByTime(300);
      component.clearFilters();
      userApiServiceMock.listUsers.mockClear();

      component.filtersForm.controls.q.setValue('martin');
      vi.advanceTimersByTime(300);

      expect(userApiServiceMock.listUsers).toHaveBeenCalledTimes(1);
    });

    it('should ignore the response of an obsolete request', () => {
      const stale = new Subject<PageResponse<UserResponse>>();
      const fresh = new Subject<PageResponse<UserResponse>>();
      userApiServiceMock.listUsers.mockReturnValueOnce(stale).mockReturnValueOnce(fresh);

      component.filtersForm.controls.accountStatus.setValue('ACTIVE');
      component.filtersForm.controls.accountStatus.setValue('DISABLED');
      fresh.next(buildPage([buildUser({ id: 2, firstName: 'Récent' })], 1));
      stale.next(buildPage([buildUser({ id: 1, firstName: 'Obsolète' })], 1));

      expect(component.rows().map((r) => r.firstName)).toEqual(['Récent']);
    });

    it('should show the filtered empty message and keep the filters visible', () => {
      userApiServiceMock.listUsers.mockReturnValue(of(buildPage([], 0)));

      component.filtersForm.controls.accountStatus.setValue('DISABLED');
      fixture.detectChanges();

      const root = fixture.nativeElement as HTMLElement;
      expect(root.querySelector('app-empty-state')?.textContent).toContain(
        'Aucun utilisateur ne correspond à votre recherche.',
      );
      expect(root.querySelector('#admin-users-search')).not.toBeNull();
      expect(root.querySelector('p-button[label="Effacer"]')).not.toBeNull();
    });
  });
});
