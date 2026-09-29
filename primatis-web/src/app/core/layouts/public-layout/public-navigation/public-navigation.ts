import { NgTemplateOutlet } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { Drawer } from 'primeng/drawer';

import { AuthService } from '../../../../auth/services/auth.service';
import { AccountMenu } from '../../../../shared/navigation/account-menu/account-menu';
import {
  NavigationItem,
  isNavigationItemVisible,
} from '../../../../shared/navigation/navigation-item';


@Component({
  selector: 'app-public-navigation',
  imports: [RouterLink, RouterLinkActive, NgTemplateOutlet, ButtonModule, Drawer, AccountMenu],
  templateUrl: './public-navigation.html',
  styleUrl: './public-navigation.scss',
})
export class PublicNavigation {
  private readonly authService = inject(AuthService);

  readonly mobileMenuOpen = signal(false);

  private static readonly ITEMS: readonly NavigationItem[] = [
    { label: 'Accueil', routerLink: '/' },
    
    
    { label: 'Catalogue', routerLink: '/catalogue' },
    
    // surface backend permitAll, même statut exact que Catalogue.
    { label: 'Articles', routerLink: '/articles' },
    
    // statique, aucun requiredRoles — même statut que Catalogue/Articles.
    { label: 'Georges Lemaître', routerLink: '/georges-lemaitre' },
    
    // routes/permissions que l'ancienne navigation unique, mais ne mènent
    // plus qu'à l'écran d'accueil de chaque univers — la navigation
    // détaillée de cet univers prend ensuite le relais (MemberNavigation /
    // StaffAdminShell).
    { label: 'Espace membre', routerLink: '/member/profile', requiredRoles: ['ROLE_MEMBER'] },
    {
      label: 'Gestion bibliothèque',
      routerLink: '/staff/users',
      requiredRoles: ['ROLE_LIBRARIAN', 'ROLE_ADMIN'],
    },
    { label: 'Administration', routerLink: '/admin/users', requiredRoles: ['ROLE_ADMIN'] },
  ];

  visibleItems(): readonly NavigationItem[] {
    const authenticated = this.authService.authenticated();
    const roles = this.authService.roles();
    const permissions = this.authService.permissions();
    return PublicNavigation.ITEMS.filter((item) =>
      isNavigationItemVisible(item, authenticated, roles, permissions),
    );
  }

  openMobileMenu(): void {
    this.mobileMenuOpen.set(true);
  }

  closeMobileMenu(): void {
    this.mobileMenuOpen.set(false);
  }
}
