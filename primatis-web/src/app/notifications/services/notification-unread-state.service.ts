import { Injectable, inject, signal } from '@angular/core';
import { finalize } from 'rxjs';

import { NotificationApiService } from './notification-api.service';


@Injectable({ providedIn: 'root' })
export class NotificationUnreadStateService {
  private readonly notificationApiService = inject(NotificationApiService);
  private readonly countSignal = signal(0);
  private refreshInFlight = false;

  readonly unreadCount = this.countSignal.asReadonly();

  /**
   * Rafraîchissement passif : une erreur ne doit jamais perturber la
   * navigation globale (aucun Toast, aucun état d'erreur exposé ici) — la
   * page `MemberNotificationsPage` porte sa propre gestion d'erreur pour la
   * consultation complète.
   */
  refresh(): void {
    if (this.refreshInFlight) {
      return;
    }
    this.refreshInFlight = true;

    this.notificationApiService
      .getUnreadCount()
      .pipe(finalize(() => (this.refreshInFlight = false)))
      .subscribe({
        next: (response) => this.countSignal.set(response.count),
        error: () => {
          // Échec silencieux, volontaire (voir Javadoc de la méthode).
        },
      });
  }

  decrement(): void {
    this.countSignal.update((current) => Math.max(0, current - 1));
  }

  
  reset(): void {
    this.countSignal.set(0);
  }
}
