import { Component, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { OverlayBadge } from 'primeng/overlaybadge';

import { AuthService } from '../../../auth/services/auth.service';
import { NotificationUnreadStateService } from '../../../notifications/services/notification-unread-state.service';


@Component({
  selector: 'app-account-menu',
  imports: [RouterLink, OverlayBadge, ButtonModule],
  templateUrl: './account-menu.html',
  styleUrl: './account-menu.scss',
})
export class AccountMenu {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly unreadState = inject(NotificationUnreadStateService);

  constructor() {
    if (this.authenticated()) {
      this.unreadState.refresh();
    }
  }

  authenticated(): boolean {
    return this.authService.authenticated();
  }

  unreadCount(): number {
    return this.unreadState.unreadCount();
  }

  logout(): void {
    this.unreadState.reset();
    this.authService.logout();
    void this.router.navigateByUrl('/');
  }
}
