import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ConfirmationService, MessageService } from 'primeng/api';
import { Subject, of, throwError } from 'rxjs';
import { afterEach, vi } from 'vitest';

import { AuthService } from '../../../../auth/services/auth.service';
import { PageResponse } from '../../../../core/models/page-response';
import { isoDateToDate } from '../../../../shared/date/iso-date';
import { FineResponse } from '../../../../fines/models/fine-response';
import { FineApiService } from '../../../../fines/services/fine-api.service';
import { StaffFinesPage } from './staff-fines-page';

function buildFine(overrides: Partial<FineResponse> = {}): FineResponse {
  return {
    id: 1,
    borrower: { id: 7, memberNumber: 'M000000001', firstName: 'Marie', lastName: 'Curie' },
    loan: {
      id: 20,
      loanDate: '2026-07-01T09:00:00Z',
      dueDate: '2026-07-22',
      returnDate: '2026-08-05',
      copy: { id: 30, inventoryCode: 'INV-000030', titleId: 40, coverImageUrl: null },
    },
    amount: 11.2,
    reason: 'Retard de 2 semaine(s)',
    issuedAt: '2026-08-05T10:00:00Z',
    fineStatus: 'UNPAID',
    paidAt: null,
    cancelledAt: null,
    ...overrides,
  };
}

function buildPage(content: FineResponse[], totalElements = content.length): PageResponse<FineResponse> {
  return { content, page: 0, size: 20, totalElements, totalPages: Math.max(1, Math.ceil(totalElements / 20)) };
}

function apiHttpError(status: number, code: string, message: string): HttpErrorResponse {
  return new HttpErrorResponse({
    status,
    error: {
      timestamp: new Date().toISOString(),
      status,
      error: 'Error',
      code,
      message,
      path: '/api/v1/fines',
      fieldErrors: [],
    },
  });
}

describe('StaffFinesPage', () => {
  let fixture: ComponentFixture<StaffFinesPage>;
  let fineApiServiceMock: {
    listOwnFines: ReturnType<typeof vi.fn>;
    listFines: ReturnType<typeof vi.fn>;
    confirmPayment: ReturnType<typeof vi.fn>;
    cancelFine: ReturnType<typeof vi.fn>;
  };
  let authServiceMock: { hasPermission: ReturnType<typeof vi.fn> };
  let messageServiceMock: { add: ReturnType<typeof vi.fn> };
  let confirmationServiceMock: { confirm: ReturnType<typeof vi.fn> };

  function configure(): void {
    fineApiServiceMock = {
      listOwnFines: vi.fn(),
      listFines: vi.fn(),
      confirmPayment: vi.fn(),
      cancelFine: vi.fn(),
    };
    authServiceMock = { hasPermission: vi.fn().mockReturnValue(true) };
    messageServiceMock = { add: vi.fn() };
    confirmationServiceMock = { confirm: vi.fn() };

    TestBed.configureTestingModule({
      imports: [StaffFinesPage],
      providers: [
        { provide: FineApiService, useValue: fineApiServiceMock },
        { provide: AuthService, useValue: authServiceMock },
        { provide: MessageService, useValue: messageServiceMock },
        { provide: ConfirmationService, useValue: confirmationServiceMock },
      ],
    });
  }

  function createComponent(): void {
    fixture = TestBed.createComponent(StaffFinesPage);
    fixture.detectChanges();
  }

  
  // Chargement / contrat API
  

  it('should call listFines(0, 20) on initial load', () => {
    configure();
    fineApiServiceMock.listFines.mockReturnValue(of(buildPage([buildFine()])));

    createComponent();

    expect(fineApiServiceMock.listFines).toHaveBeenCalledWith(0, 20, {});
  });

  it('should set the document title (DEV-15.8)', () => {
    configure();
    fineApiServiceMock.listFines.mockReturnValue(of(buildPage([buildFine()])));

    createComponent();

    expect(document.title).toBe('Amendes — PRIMATIS');
  });

  it('should never call the self-service listOwnFines endpoint', () => {
    configure();
    fineApiServiceMock.listFines.mockReturnValue(of(buildPage([buildFine()])));

    createComponent();
    fixture.componentInstance.onLazyLoad({ first: 20, rows: 20 });
    fixture.componentInstance.retry();

    expect(fineApiServiceMock.listOwnFines).not.toHaveBeenCalled();
  });

  it('should map a PrimeNG lazy load event to page/size and call listFines again', () => {
    configure();
    fineApiServiceMock.listFines.mockReturnValue(of(buildPage([buildFine()], 100)));
    createComponent();
    fineApiServiceMock.listFines.mockClear();

    fixture.componentInstance.onLazyLoad({ first: 40, rows: 20 });

    expect(fineApiServiceMock.listFines).toHaveBeenCalledWith(2, 20, {});
  });

  it('should default to page 0 / size 20 when the lazy load event omits first/rows', () => {
    configure();
    fineApiServiceMock.listFines.mockReturnValue(of(buildPage([buildFine()])));
    createComponent();
    fineApiServiceMock.listFines.mockClear();

    fixture.componentInstance.onLazyLoad({});

    expect(fineApiServiceMock.listFines).toHaveBeenCalledWith(0, 20, {});
  });

  it('should show the loading state before the first response arrives', () => {
    configure();
    fineApiServiceMock.listFines.mockReturnValue({ subscribe: () => ({ unsubscribe: () => {} }) });

    createComponent();

    expect(fixture.nativeElement.querySelector('app-loading-state')).not.toBeNull();
  });

  it('should show the empty state when the backend returns no fine', () => {
    configure();
    fineApiServiceMock.listFines.mockReturnValue(of(buildPage([], 0)));

    createComponent();

    expect(fixture.nativeElement.querySelector('app-empty-state')).not.toBeNull();
  });

  it('should show the error state on a failed request', () => {
    configure();
    fineApiServiceMock.listFines.mockReturnValue(throwError(() => apiHttpError(500, 'INTERNAL_ERROR', 'Erreur serveur.')));

    createComponent();

    expect(fixture.nativeElement.querySelector('app-error-state')).not.toBeNull();
  });

  it('should retry the last request when retry is triggered', () => {
    configure();
    fineApiServiceMock.listFines.mockReturnValue(throwError(() => apiHttpError(500, 'INTERNAL_ERROR', 'Erreur serveur.')));
    createComponent();
    fineApiServiceMock.listFines.mockClear();
    fineApiServiceMock.listFines.mockReturnValue(of(buildPage([buildFine()])));

    fixture.componentInstance.retry();

    expect(fineApiServiceMock.listFines).toHaveBeenCalledWith(0, 20, {});
  });

  
  // Permissions UX — FINE_READ (implicite, route) vs FINE_MANAGE
  

  it('should show no action and no Actions column when the user only has FINE_READ', () => {
    configure();
    authServiceMock.hasPermission.mockImplementation((permission: string) => permission !== 'FINE_MANAGE');
    fineApiServiceMock.listFines.mockReturnValue(of(buildPage([buildFine({ fineStatus: 'UNPAID' })])));

    createComponent();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).not.toContain('Confirmer paiement');
    expect(text).not.toContain('Annuler');
    expect(text).not.toContain('Actions');
    const buttons: HTMLButtonElement[] = Array.from(fixture.nativeElement.querySelectorAll('button'));
    const nonPaginatorButtons = buttons.filter((button) => !button.closest('.p-paginator'));
    expect(nonPaginatorButtons).toHaveLength(0);
    expect(authServiceMock.hasPermission).toHaveBeenCalledWith('FINE_MANAGE');
  });

  it('should show both actions for an UNPAID fine when the user has FINE_MANAGE', () => {
    configure();
    authServiceMock.hasPermission.mockReturnValue(true);
    fineApiServiceMock.listFines.mockReturnValue(of(buildPage([buildFine({ id: 1, fineStatus: 'UNPAID' })])));

    createComponent();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Confirmer paiement');
    expect(text).toContain('Annuler');
  });

  it('should never show an action for a PAID fine, even with FINE_MANAGE', () => {
    configure();
    authServiceMock.hasPermission.mockReturnValue(true);
    fineApiServiceMock.listFines.mockReturnValue(
      of(buildPage([buildFine({ id: 1, fineStatus: 'PAID', paidAt: '2026-08-10T09:00:00Z' })])),
    );

    createComponent();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).not.toContain('Confirmer paiement');
    expect(text).not.toContain('Annuler');
  });

  it('should never show an action for a CANCELLED fine, even with FINE_MANAGE', () => {
    configure();
    authServiceMock.hasPermission.mockReturnValue(true);
    fineApiServiceMock.listFines.mockReturnValue(
      of(buildPage([buildFine({ id: 1, fineStatus: 'CANCELLED', cancelledAt: '2026-08-10T09:00:00Z' })])),
    );

    createComponent();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).not.toContain('Confirmer paiement');
    expect(text).not.toContain('Annuler');
  });

  
  // Confirmation du paiement
  

  it('should require confirmation before calling confirmPayment', () => {
    configure();
    const fine = buildFine({ id: 1, fineStatus: 'UNPAID' });
    fineApiServiceMock.listFines.mockReturnValue(of(buildPage([fine])));
    createComponent();

    fixture.componentInstance.confirmPaymentPrompt(fine);

    expect(confirmationServiceMock.confirm).toHaveBeenCalledTimes(1);
    expect(fineApiServiceMock.confirmPayment).not.toHaveBeenCalled();
  });

  it('should make no HTTP call when the payment confirmation prompt is not accepted', () => {
    configure();
    const fine = buildFine({ id: 1, fineStatus: 'UNPAID' });
    fineApiServiceMock.listFines.mockReturnValue(of(buildPage([fine])));
    createComponent();

    fixture.componentInstance.confirmPaymentPrompt(fine);
    // La confirmation n'est jamais acceptée : aucun accept() invoqué.

    expect(fineApiServiceMock.confirmPayment).not.toHaveBeenCalled();
  });

  it('should call confirmPayment(id) once confirmation is accepted', () => {
    configure();
    const fine = buildFine({ id: 1, fineStatus: 'UNPAID' });
    fineApiServiceMock.listFines.mockReturnValue(of(buildPage([fine])));
    fineApiServiceMock.confirmPayment.mockReturnValue(
      of(buildFine({ id: 1, fineStatus: 'PAID', paidAt: '2026-08-20T10:00:00Z' })),
    );
    createComponent();

    fixture.componentInstance.confirmPaymentPrompt(fine);
    confirmationServiceMock.confirm.mock.calls[0][0].accept();

    expect(fineApiServiceMock.confirmPayment).toHaveBeenCalledWith(1);
  });

  it('should disable both action buttons on the row while the payment request is pending', () => {
    configure();
    const fine = buildFine({ id: 1, fineStatus: 'UNPAID' });
    fineApiServiceMock.listFines.mockReturnValue(of(buildPage([fine])));
    const pending = new Subject<FineResponse>();
    fineApiServiceMock.confirmPayment.mockReturnValue(pending.asObservable());
    createComponent();

    fixture.componentInstance.confirmPaymentPrompt(fine);
    confirmationServiceMock.confirm.mock.calls[0][0].accept();
    fixture.detectChanges();

    const buttons: HTMLButtonElement[] = Array.from(fixture.nativeElement.querySelectorAll('button'));
    const nonPaginatorButtons = buttons.filter((button) => !button.closest('.p-paginator'));
    expect(nonPaginatorButtons.every((button) => button.disabled)).toBe(true);

    pending.next(buildFine({ id: 1, fineStatus: 'PAID', paidAt: '2026-08-20T10:00:00Z' }));
    pending.complete();
  });

  it('should show a success toast and replace the row with the exact backend response after a successful payment confirmation', () => {
    configure();
    const fine = buildFine({ id: 1, fineStatus: 'UNPAID' });
    const paid = buildFine({ id: 1, fineStatus: 'PAID', paidAt: '2026-08-20T10:00:00Z' });
    fineApiServiceMock.listFines.mockReturnValue(of(buildPage([fine])));
    fineApiServiceMock.confirmPayment.mockReturnValue(of(paid));
    createComponent();

    fixture.componentInstance.confirmPaymentPrompt(fine);
    confirmationServiceMock.confirm.mock.calls[0][0].accept();

    expect(fixture.componentInstance.rows()).toEqual([paid]);
    expect(messageServiceMock.add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'success' }));
  });

  it('should show an error toast and never mutate the row when confirmPayment fails', () => {
    configure();
    const fine = buildFine({ id: 1, fineStatus: 'UNPAID' });
    fineApiServiceMock.listFines.mockReturnValue(of(buildPage([fine])));
    fineApiServiceMock.confirmPayment.mockReturnValue(
      throwError(() => apiHttpError(409, 'FINE_NOT_PAYABLE', 'Cette amende n’est plus payable.')),
    );
    createComponent();

    fixture.componentInstance.confirmPaymentPrompt(fine);
    confirmationServiceMock.confirm.mock.calls[0][0].accept();

    expect(fixture.componentInstance.rows()).toEqual([fine]);
    expect(messageServiceMock.add).toHaveBeenCalledWith(
      expect.objectContaining({ severity: 'error', detail: 'Cette amende n’est plus payable.' }),
    );
  });

  
  // Annulation
  

  it('should require confirmation before calling cancelFine', () => {
    configure();
    const fine = buildFine({ id: 1, fineStatus: 'UNPAID' });
    fineApiServiceMock.listFines.mockReturnValue(of(buildPage([fine])));
    createComponent();

    fixture.componentInstance.confirmCancelPrompt(fine);

    expect(confirmationServiceMock.confirm).toHaveBeenCalledTimes(1);
    expect(fineApiServiceMock.cancelFine).not.toHaveBeenCalled();
  });

  it('should make no HTTP call when the cancellation prompt is not accepted', () => {
    configure();
    const fine = buildFine({ id: 1, fineStatus: 'UNPAID' });
    fineApiServiceMock.listFines.mockReturnValue(of(buildPage([fine])));
    createComponent();

    fixture.componentInstance.confirmCancelPrompt(fine);

    expect(fineApiServiceMock.cancelFine).not.toHaveBeenCalled();
  });

  it('should call cancelFine(id) once confirmation is accepted', () => {
    configure();
    const fine = buildFine({ id: 1, fineStatus: 'UNPAID' });
    fineApiServiceMock.listFines.mockReturnValue(of(buildPage([fine])));
    fineApiServiceMock.cancelFine.mockReturnValue(
      of(buildFine({ id: 1, fineStatus: 'CANCELLED', cancelledAt: '2026-08-20T10:00:00Z' })),
    );
    createComponent();

    fixture.componentInstance.confirmCancelPrompt(fine);
    confirmationServiceMock.confirm.mock.calls[0][0].accept();

    expect(fineApiServiceMock.cancelFine).toHaveBeenCalledWith(1);
  });

  it('should disable both action buttons on the row while the cancellation request is pending', () => {
    configure();
    const fine = buildFine({ id: 1, fineStatus: 'UNPAID' });
    fineApiServiceMock.listFines.mockReturnValue(of(buildPage([fine])));
    const pending = new Subject<FineResponse>();
    fineApiServiceMock.cancelFine.mockReturnValue(pending.asObservable());
    createComponent();

    fixture.componentInstance.confirmCancelPrompt(fine);
    confirmationServiceMock.confirm.mock.calls[0][0].accept();
    fixture.detectChanges();

    const buttons: HTMLButtonElement[] = Array.from(fixture.nativeElement.querySelectorAll('button'));
    const nonPaginatorButtons = buttons.filter((button) => !button.closest('.p-paginator'));
    expect(nonPaginatorButtons.every((button) => button.disabled)).toBe(true);

    pending.next(buildFine({ id: 1, fineStatus: 'CANCELLED', cancelledAt: '2026-08-20T10:00:00Z' }));
    pending.complete();
  });

  it('should show a success toast and replace the row with the exact backend response after a successful cancellation', () => {
    configure();
    const fine = buildFine({ id: 1, fineStatus: 'UNPAID' });
    const cancelled = buildFine({ id: 1, fineStatus: 'CANCELLED', cancelledAt: '2026-08-20T10:00:00Z' });
    fineApiServiceMock.listFines.mockReturnValue(of(buildPage([fine])));
    fineApiServiceMock.cancelFine.mockReturnValue(of(cancelled));
    createComponent();

    fixture.componentInstance.confirmCancelPrompt(fine);
    confirmationServiceMock.confirm.mock.calls[0][0].accept();

    expect(fixture.componentInstance.rows()).toEqual([cancelled]);
    expect(messageServiceMock.add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'success' }));
  });

  it('should show an error toast and never mutate the row when cancelFine fails', () => {
    configure();
    const fine = buildFine({ id: 1, fineStatus: 'UNPAID' });
    fineApiServiceMock.listFines.mockReturnValue(of(buildPage([fine])));
    fineApiServiceMock.cancelFine.mockReturnValue(
      throwError(() => apiHttpError(409, 'FINE_NOT_CANCELLABLE', 'Cette amende n’est plus annulable.')),
    );
    createComponent();

    fixture.componentInstance.confirmCancelPrompt(fine);
    confirmationServiceMock.confirm.mock.calls[0][0].accept();

    expect(fixture.componentInstance.rows()).toEqual([fine]);
    expect(messageServiceMock.add).toHaveBeenCalledWith(
      expect.objectContaining({ severity: 'error', detail: 'Cette amende n’est plus annulable.' }),
    );
  });

  
  // Rendu
  

  it('should render the amount formatted as an EUR currency, not a raw number', () => {
    configure();
    fineApiServiceMock.listFines.mockReturnValue(of(buildPage([buildFine({ amount: 11.2 })])));

    createComponent();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('€');
  });

  it('should render borrower firstName/lastName/memberNumber', () => {
    configure();
    fineApiServiceMock.listFines.mockReturnValue(
      of(
        buildPage([
          buildFine({ borrower: { id: 7, memberNumber: 'M000000042', firstName: 'Ada', lastName: 'Lovelace' } }),
        ]),
      ),
    );

    createComponent();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Ada');
    expect(text).toContain('Lovelace');
    expect(text).toContain('M000000042');
  });

  it('should render UNPAID/PAID/CANCELLED status labels', () => {
    configure();
    fineApiServiceMock.listFines.mockReturnValue(
      of(
        buildPage([
          buildFine({ id: 1, fineStatus: 'UNPAID' }),
          buildFine({ id: 2, fineStatus: 'PAID', paidAt: '2026-08-10T09:00:00Z' }),
          buildFine({ id: 3, fineStatus: 'CANCELLED', cancelledAt: '2026-08-10T09:00:00Z' }),
        ]),
      ),
    );

    createComponent();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Impayée');
    expect(text).toContain('Payée');
    expect(text).toContain('Annulée');
  });

  it('should render placeholders, not raw null, when paidAt/cancelledAt are absent', () => {
    configure();
    fineApiServiceMock.listFines.mockReturnValue(
      of(buildPage([buildFine({ paidAt: null, cancelledAt: null })])),
    );

    createComponent();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('—');
    expect(text).not.toContain('null');
  });

  
  
  

  describe('date display', () => {
    it('should render issue and closure timestamps in French UTC, never as raw ISO', () => {
      configure();
      fineApiServiceMock.listFines.mockReturnValue(
        of(
          buildPage([
            buildFine({ id: 1, fineStatus: 'PAID', issuedAt: '2026-09-12T16:00:00Z', paidAt: '2026-09-15T08:30:00.123456Z' }),
            buildFine({ id: 2, fineStatus: 'CANCELLED', issuedAt: '2026-09-12T16:00:00Z', cancelledAt: '2026-09-20T23:59:59Z' }),
          ]),
        ),
      );

      createComponent();

      const root = fixture.nativeElement as HTMLElement;
      const closed = Array.from(root.querySelectorAll('td[data-label="Clôturée le"]')).map((c) => c.textContent?.trim());
      const issued = Array.from(root.querySelectorAll('td[data-label="Date d\'émission"]')).map((c) => c.textContent?.trim());
      expect(issued).toEqual(['12/09/2026 16:00 UTC', '12/09/2026 16:00 UTC']);
      expect(closed).toEqual(['15/09/2026 08:30 UTC', '20/09/2026 23:59 UTC']);
      expect(root.textContent).not.toMatch(/\d{4}-\d{2}-\d{2}T\d{2}:/);
    });

    it('should show the neutral placeholder for an unpaid fine without closure date', () => {
      configure();
      fineApiServiceMock.listFines.mockReturnValue(
        of(buildPage([buildFine({ fineStatus: 'UNPAID', paidAt: null, cancelledAt: null })])),
      );

      createComponent();

      const cell = (fixture.nativeElement as HTMLElement).querySelector('td[data-label="Clôturée le"]');
      expect(cell?.textContent?.trim()).toBe('—');
    });
  });

  
  
  

  describe('search and filters', () => {
    let component: StaffFinesPage;

    function lastCall(): unknown[] {
      return fineApiServiceMock.listFines.mock.calls.at(-1) as unknown[];
    }

    beforeEach(() => {
      vi.useFakeTimers();
      configure();
      fineApiServiceMock.listFines.mockReturnValue(of(buildPage([buildFine()], 100)));
      createComponent();
      component = fixture.componentInstance;
      fineApiServiceMock.listFines.mockClear();
    });

    afterEach(() => vi.useRealTimers());

    it('should render labelled controls with French labels and technical values', () => {
      const root = fixture.nativeElement as HTMLElement;

      expect(root.querySelector('label[for="staff-fines-search"]')?.textContent).toContain('Rechercher une amende');
      expect((root.querySelector('#staff-fines-search') as HTMLInputElement).placeholder).toBe(
        'Rechercher par membre, n° adhérent ou titre…',
      );
      expect(root.querySelector('label[for="staff-fines-status"]')?.textContent).toContain('Statut');
      expect(Array.from(root.querySelectorAll('legend')).map((l) => l.textContent?.trim())).toEqual([
        'Date d’émission',
        'Date de clôture',
      ]);
      expect(root.querySelector<HTMLInputElement>('input#staff-fines-issued-from')?.placeholder).toBe('jj/mm/aaaa');
      expect(component.fineStatusOptions.map((o) => o.label)).toEqual(['Tous les statuts', 'Impayée', 'Payée', 'Annulée']);
      expect(component.fineStatusOptions.map((o) => o.value)).toEqual([null, 'UNPAID', 'PAID', 'CANCELLED']);
    });

    it('should debounce q, trim it and reset to page 0', () => {
      component.onLazyLoad({ first: 40, rows: 20 });
      fineApiServiceMock.listFines.mockClear();

      component.filtersForm.controls.q.setValue('  hugo ');
      vi.advanceTimersByTime(299);
      expect(fineApiServiceMock.listFines).not.toHaveBeenCalled();

      vi.advanceTimersByTime(1);
      expect(fineApiServiceMock.listFines).toHaveBeenCalledTimes(1);
      expect(lastCall()).toEqual([0, 20, { q: 'hugo' }]);
    });

    it('should send one request for a burst of keystrokes and none for an unchanged trimmed q', () => {
      component.filtersForm.controls.q.setValue('h');
      vi.advanceTimersByTime(100);
      component.filtersForm.controls.q.setValue('hug');
      vi.advanceTimersByTime(300);
      expect(fineApiServiceMock.listFines).toHaveBeenCalledTimes(1);

      component.filtersForm.controls.q.setValue('hug  ');
      vi.advanceTimersByTime(300);
      expect(fineApiServiceMock.listFines).toHaveBeenCalledTimes(1);
    });

    it('should send fineStatus immediately and reset to page 0', () => {
      component.onLazyLoad({ first: 40, rows: 20 });
      fineApiServiceMock.listFines.mockClear();

      component.filtersForm.controls.fineStatus.setValue('UNPAID');

      expect(lastCall()).toEqual([0, 20, { fineStatus: 'UNPAID' }]);
      expect(component.first()).toBe(0);
    });

    it('should send the issue and closure ranges as ISO days', () => {
      component.filtersForm.controls.issuedDateFrom.setValue(isoDateToDate('2026-09-01'));
      component.filtersForm.controls.issuedDateTo.setValue(isoDateToDate('2026-09-30'));
      component.filtersForm.controls.closedDateFrom.setValue(isoDateToDate('2026-09-10'));
      component.filtersForm.controls.closedDateTo.setValue(new Date(2026, 8, 20, 23, 59, 59));

      expect(lastCall()).toEqual([
        0,
        20,
        { issuedDateFrom: '2026-09-01', issuedDateTo: '2026-09-30', closedDateFrom: '2026-09-10', closedDateTo: '2026-09-20' },
      ]);
    });

    it('should accept open-ended and same-day ranges', () => {
      component.filtersForm.controls.closedDateTo.setValue(isoDateToDate('2026-09-15'));
      expect(lastCall()).toEqual([0, 20, { closedDateTo: '2026-09-15' }]);
      component.filtersForm.controls.closedDateFrom.setValue(isoDateToDate('2026-09-15'));

      expect(lastCall()).toEqual([0, 20, { closedDateFrom: '2026-09-15', closedDateTo: '2026-09-15' }]);
    });

    it('should not reload when a datepicker re-emits an unchanged empty value (partial keystrokes)', () => {
      for (let i = 0; i < 5; i++) {
        component.filtersForm.controls.closedDateTo.setValue(null);
      }

      expect(fineApiServiceMock.listFines).not.toHaveBeenCalled();
    });

    it('should reload once for a date typed after several empty re-emissions', () => {
      component.filtersForm.controls.closedDateTo.setValue(null);
      component.filtersForm.controls.closedDateTo.setValue(null);
      component.filtersForm.controls.closedDateTo.setValue(isoDateToDate('2026-09-10'));
      component.filtersForm.controls.closedDateTo.setValue(isoDateToDate('2026-09-10'));

      expect(fineApiServiceMock.listFines).toHaveBeenCalledTimes(1);
    });

    it('should combine q, status and both ranges', () => {
      component.filtersForm.controls.fineStatus.setValue('PAID');
      component.filtersForm.controls.issuedDateFrom.setValue(isoDateToDate('2026-08-01'));
      component.filtersForm.controls.closedDateTo.setValue(isoDateToDate('2026-09-15'));
      component.filtersForm.controls.q.setValue('hugo');
      vi.advanceTimersByTime(300);

      expect(lastCall()).toEqual([
        0,
        20,
        { q: 'hugo', fineStatus: 'PAID', issuedDateFrom: '2026-08-01', closedDateTo: '2026-09-15' },
      ]);
    });

    it('should keep every criterion when the page changes', () => {
      component.filtersForm.controls.fineStatus.setValue('CANCELLED');
      component.filtersForm.controls.issuedDateFrom.setValue(isoDateToDate('2026-08-01'));
      fineApiServiceMock.listFines.mockClear();

      component.onLazyLoad({ first: 20, rows: 20 });

      expect(lastCall()).toEqual([1, 20, { fineStatus: 'CANCELLED', issuedDateFrom: '2026-08-01' }]);
      expect(component.first()).toBe(20);
    });

    it('should not call the API and should show the range message for an inverted issue range', () => {
      component.filtersForm.controls.issuedDateFrom.setValue(isoDateToDate('2026-09-10'));
      fineApiServiceMock.listFines.mockClear();

      component.filtersForm.controls.issuedDateTo.setValue(isoDateToDate('2026-09-01'));
      fixture.detectChanges();

      expect(fineApiServiceMock.listFines).not.toHaveBeenCalled();
      const root = fixture.nativeElement as HTMLElement;
      expect(root.querySelector('#staff-fines-issued-range-error')?.textContent).toContain(
        'La date de début doit précéder la date de fin.',
      );
      expect(root.querySelector('#staff-fines-closed-range-error')).toBeNull();
    });

    it('should not call the API and should show the range message for an inverted closure range', () => {
      component.filtersForm.controls.closedDateFrom.setValue(isoDateToDate('2026-09-10'));
      fineApiServiceMock.listFines.mockClear();

      component.filtersForm.controls.closedDateTo.setValue(isoDateToDate('2026-09-01'));
      fixture.detectChanges();

      expect(fineApiServiceMock.listFines).not.toHaveBeenCalled();
      expect((fixture.nativeElement as HTMLElement).querySelector('#staff-fines-closed-range-error')).not.toBeNull();
    });

    it('should search again once an inverted range is corrected', () => {
      component.filtersForm.controls.closedDateFrom.setValue(isoDateToDate('2026-09-10'));
      component.filtersForm.controls.closedDateTo.setValue(isoDateToDate('2026-09-01'));
      fineApiServiceMock.listFines.mockClear();

      component.filtersForm.controls.closedDateTo.setValue(isoDateToDate('2026-09-30'));

      expect(lastCall()).toEqual([0, 20, { closedDateFrom: '2026-09-10', closedDateTo: '2026-09-30' }]);
    });

    it('should show the clear button only when a criterion is active', () => {
      const root = fixture.nativeElement as HTMLElement;
      expect(root.querySelector('p-button[label="Effacer"]')).toBeNull();

      component.filtersForm.controls.fineStatus.setValue('UNPAID');
      fixture.detectChanges();

      expect(root.querySelector('p-button[label="Effacer"]')).not.toBeNull();
    });

    it('should clear every criterion with a single request on page 0', () => {
      component.filtersForm.controls.fineStatus.setValue('PAID');
      component.filtersForm.controls.issuedDateFrom.setValue(isoDateToDate('2026-08-01'));
      component.filtersForm.controls.closedDateTo.setValue(isoDateToDate('2026-09-15'));
      component.filtersForm.controls.q.setValue('hugo');
      vi.advanceTimersByTime(300);
      component.onLazyLoad({ first: 20, rows: 20 });
      fineApiServiceMock.listFines.mockClear();

      component.clearFilters();
      vi.advanceTimersByTime(1000);

      expect(fineApiServiceMock.listFines).toHaveBeenCalledTimes(1);
      expect(lastCall()).toEqual([0, 20, {}]);
      expect(component.filtersForm.getRawValue()).toEqual({
        q: '',
        fineStatus: null,
        issuedDateFrom: null,
        issuedDateTo: null,
        closedDateFrom: null,
        closedDateTo: null,
      });
      expect(component.hasActiveFilters()).toBe(false);
    });

    it('should clear an inverted range without sending an invalid request', () => {
      component.filtersForm.controls.closedDateFrom.setValue(isoDateToDate('2026-09-10'));
      component.filtersForm.controls.closedDateTo.setValue(isoDateToDate('2026-09-01'));
      fineApiServiceMock.listFines.mockClear();

      component.clearFilters();

      expect(lastCall()).toEqual([0, 20, {}]);
      expect(component.filtersForm.valid).toBe(true);
    });

    it('should search again for the same q after clearing', () => {
      component.filtersForm.controls.q.setValue('hugo');
      vi.advanceTimersByTime(300);
      component.clearFilters();
      fineApiServiceMock.listFines.mockClear();

      component.filtersForm.controls.q.setValue('hugo');
      vi.advanceTimersByTime(300);

      expect(lastCall()).toEqual([0, 20, { q: 'hugo' }]);
    });

    it('should show the search-specific empty state and keep the filters visible', () => {
      fineApiServiceMock.listFines.mockReturnValue(of(buildPage([], 0)));

      component.filtersForm.controls.fineStatus.setValue('PAID');
      fixture.detectChanges();

      const root = fixture.nativeElement as HTMLElement;
      expect(root.querySelector('app-empty-state')?.textContent).toContain('Aucune amende ne correspond à votre recherche.');
      expect(root.querySelector('#staff-fines-search')).not.toBeNull();
      expect(root.querySelector('p-button[label="Effacer"]')).not.toBeNull();
    });

    it('should ignore a stale response arriving after a newer request', () => {
      const first$ = new Subject<PageResponse<FineResponse>>();
      const second$ = new Subject<PageResponse<FineResponse>>();
      fineApiServiceMock.listFines.mockReturnValueOnce(first$).mockReturnValueOnce(second$);

      component.filtersForm.controls.fineStatus.setValue('UNPAID');
      component.filtersForm.controls.fineStatus.setValue('PAID');
      second$.next(buildPage([buildFine({ id: 2 })], 1));
      first$.next(buildPage([buildFine({ id: 1 }), buildFine({ id: 3 })], 2));

      expect(component.rows().map((f) => f.id)).toEqual([2]);
      expect(component.totalRecords()).toBe(1);
    });

    // Actions sous filtre actif : la ligne payée/annulée ne doit pas rester artificiellement visible.
    it('should reload the current page with the active criteria after a payment, not keep a stale row', () => {
      component.filtersForm.controls.fineStatus.setValue('UNPAID');
      const unpaid = buildFine({ id: 1, fineStatus: 'UNPAID' });
      const paid = buildFine({ id: 1, fineStatus: 'PAID', paidAt: '2026-08-20T10:00:00Z' });
      fineApiServiceMock.listFines.mockReturnValue(of(buildPage([unpaid], 1)));
      component.onLazyLoad({ first: 0, rows: 20 });
      fineApiServiceMock.confirmPayment.mockReturnValue(of(paid));
      fineApiServiceMock.listFines.mockClear();
      fineApiServiceMock.listFines.mockReturnValue(of(buildPage([], 0)));

      component.confirmPaymentPrompt(unpaid);
      confirmationServiceMock.confirm.mock.calls[0][0].accept();

      expect(fineApiServiceMock.listFines).toHaveBeenCalledTimes(1);
      expect(lastCall()).toEqual([0, 20, { fineStatus: 'UNPAID' }]);
      expect(component.rows()).toEqual([]);
      expect(messageServiceMock.add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'success' }));
    });

    it('should reload with the active criteria after a cancellation', () => {
      component.filtersForm.controls.fineStatus.setValue('UNPAID');
      const unpaid = buildFine({ id: 1, fineStatus: 'UNPAID' });
      fineApiServiceMock.cancelFine.mockReturnValue(
        of(buildFine({ id: 1, fineStatus: 'CANCELLED', cancelledAt: '2026-08-20T10:00:00Z' })),
      );
      fineApiServiceMock.listFines.mockClear();
      fineApiServiceMock.listFines.mockReturnValue(of(buildPage([], 0)));

      component.confirmCancelPrompt(unpaid);
      confirmationServiceMock.confirm.mock.calls[0][0].accept();

      expect(lastCall()).toEqual([0, 20, { fineStatus: 'UNPAID' }]);
      expect(component.rows()).toEqual([]);
    });

    it('should step back one page when the action empties the last page', () => {
      component.filtersForm.controls.fineStatus.setValue('UNPAID');
      component.onLazyLoad({ first: 20, rows: 20 });
      const unpaid = buildFine({ id: 41, fineStatus: 'UNPAID' });
      fineApiServiceMock.confirmPayment.mockReturnValue(of(buildFine({ id: 41, fineStatus: 'PAID', paidAt: '2026-08-20T10:00:00Z' })));
      fineApiServiceMock.listFines.mockClear();
      fineApiServiceMock.listFines
        .mockReturnValueOnce(of({ content: [], page: 1, size: 20, totalElements: 20, totalPages: 1 }))
        .mockReturnValueOnce(of(buildPage([buildFine({ id: 40 })], 20)));

      component.confirmPaymentPrompt(unpaid);
      confirmationServiceMock.confirm.mock.calls[0][0].accept();

      expect(fineApiServiceMock.listFines.mock.calls.map((c) => c[0])).toEqual([1, 0]);
      expect(component.rows().map((f) => f.id)).toEqual([40]);
      expect(component.first()).toBe(0);
    });
  });
});
