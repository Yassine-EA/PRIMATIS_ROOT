import { Component, computed, inject, signal } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { RouterLink } from '@angular/router';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { PaginatorModule, PaginatorState } from 'primeng/paginator';
import { TableLazyLoadEvent } from 'primeng/table';
import { TagModule } from 'primeng/tag';

import { AppError } from '../../../../core/errors/api-error';
import { toAppError } from '../../../../core/errors/api-error.util';
import { NotificationResponse } from '../../../../notifications/models/notification-response';
import { NotificationType } from '../../../../notifications/models/notification-type';
import { NotificationApiService } from '../../../../notifications/services/notification-api.service';
import { NotificationUnreadStateService } from '../../../../notifications/services/notification-unread-state.service';
import { EmptyState } from '../../../../shared/ui/empty-state/empty-state';
import { ErrorState } from '../../../../shared/ui/error-state/error-state';
import { LoadingState } from '../../../../shared/ui/loading-state/loading-state';

const DEFAULT_PAGE_SIZE = 20;


@Component({
  selector: 'app-member-notifications-page',
  imports: [
    RouterLink,
    PaginatorModule,
    TagModule,
    ButtonModule,
    LoadingState,
    EmptyState,
    ErrorState,
  ],
  templateUrl: './member-notifications-page.html',
  styleUrl: './member-notifications-page.scss',
})
export class MemberNotificationsPage {
  private readonly notificationApiService = inject(NotificationApiService);
  private readonly unreadState = inject(NotificationUnreadStateService);
  private readonly messageService = inject(MessageService);
  private readonly titleService = inject(Title);

  readonly rows = signal<NotificationResponse[]>([]);
  readonly totalRecords = signal(0);
  // Initialisé à true : le premier chargement est déclenché explicitement
  // ci-dessous (constructeur), jamais par le déclenchement automatique
  // PrimeNG au montage — même principe que MemberFinesPage/MemberReservationsPage.
  readonly loading = signal(true);
  readonly error = signal<AppError | null>(null);
  readonly markingReadId = signal<number | null>(null);
  readonly markingAll = signal(false);
  readonly currentFirst = signal(0);
  readonly unreadCount = computed(
    () => this.rows().filter((notification) => notification.notificationStatus === 'UNREAD').length,
  );

  private lastPage = 0;
  private lastSize = DEFAULT_PAGE_SIZE;

  constructor() {
    this.titleService.setTitle('Notifications — PRIMATIS');
    this.load(0, DEFAULT_PAGE_SIZE);
    // Arrivée sur la page = moment UX naturel de rafraîchissement du
    
    this.unreadState.refresh();
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
    this.load(Math.floor(first / rows), rows);
  }

  retry(): void {
    this.load(this.lastPage, this.lastSize);
  }

  
  notificationIcon(type: NotificationType): string {
    if (type.startsWith('LOAN_')) {
      return 'pi-book';
    }
    if (type.startsWith('RESERVATION_')) {
      return 'pi-bookmark';
    }
    if (type.startsWith('FINE_')) {
      return 'pi-euro';
    }
    return 'pi-file';
  }

  notificationCategory(type: NotificationType): 'loan' | 'reservation' | 'fine' | 'article' {
    if (type.startsWith('LOAN_')) {
      return 'loan';
    }
    if (type.startsWith('RESERVATION_')) {
      return 'reservation';
    }
    if (type.startsWith('FINE_')) {
      return 'fine';
    }
    return 'article';
  }

  notificationCategoryLabel(type: NotificationType): string {
    switch (this.notificationCategory(type)) {
      case 'loan':
        return 'Prêt';
      case 'reservation':
        return 'Réservation';
      case 'fine':
        return 'Amende';
      case 'article':
        return 'Actualité';
    }
  }

  notificationRoute(type: NotificationType): string[] {
    switch (this.notificationCategory(type)) {
      case 'loan':
        return ['/member/loans'];
      case 'reservation':
        return ['/member/reservations'];
      case 'fine':
        return ['/member/fines'];
      case 'article':
        return ['/articles'];
    }
  }

  formatDateTime(value: string): string {
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
      return value;
    }

    return new Intl.DateTimeFormat('fr-BE', {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      timeZone: 'UTC',
    })
      .format(date)
      .replace('.', '');
  }

  markAsRead(notification: NotificationResponse): void {
    if (notification.notificationStatus !== 'UNREAD') {
      return;
    }
    this.markingReadId.set(notification.id);
    this.notificationApiService.markAsRead(notification.id).subscribe({
      next: (updated) => {
        this.markingReadId.set(null);
        this.rows.set(this.rows().map((row) => (row.id === updated.id ? updated : row)));
        this.unreadState.decrement();
      },
      error: (err: unknown) => {
        this.markingReadId.set(null);
        this.messageService.add({
          severity: 'error',
          summary: 'Erreur',
          detail: toAppError(err).message,
        });
      },
    });
  }

  markAllAsRead(): void {
    this.markingAll.set(true);
    this.notificationApiService.markAllAsRead().subscribe({
      next: (result) => {
        this.markingAll.set(false);
        this.messageService.add({
          severity: 'success',
          summary: 'Notifications marquées comme lues',
          detail:
            result.updatedCount > 0
              ? `${result.updatedCount} notification(s) marquée(s) comme lue(s).`
              : 'Aucune notification à marquer comme lue.',
        });
        this.load(this.lastPage, this.lastSize);
        this.unreadState.refresh();
      },
      error: (err: unknown) => {
        this.markingAll.set(false);
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
    this.currentFirst.set(page * size);
    this.loading.set(true);
    this.error.set(null);

    this.notificationApiService.listOwnNotifications(page, size).subscribe({
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
