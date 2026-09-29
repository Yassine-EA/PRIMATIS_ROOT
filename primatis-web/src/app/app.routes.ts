import { Routes } from '@angular/router';

import { AdminLayout } from './core/layouts/admin-layout/admin-layout';
import { MemberLayout } from './core/layouts/member-layout/member-layout';
import { PublicLayout } from './core/layouts/public-layout/public-layout';
import { StaffLayout } from './core/layouts/staff-layout/staff-layout';
import { authGuard } from './core/guards/auth.guard';
import { permissionGuard } from './core/guards/permission.guard';
import { roleGuard } from './core/guards/role.guard';
import { Forbidden } from './shared/pages/forbidden/forbidden';
import { Home } from './shared/pages/home/home';
import { NotFound } from './shared/pages/not-found/not-found';

export const routes: Routes = [
  {
    path: '',
    component: PublicLayout,
    children: [
      {
        path: '',
        component: Home,
      },
      {
        
        // l'autorité de sécurité reste le backend Spring Security.
        // Lazy loading : évite d'embarquer Login et ses modules PrimeNG
        // (Password/Message/InputText) dans le bundle initial.
        path: 'login',
        loadComponent: () => import('./auth/pages/login/login').then((m) => m.Login),
      },
      {
        
        
        // guard, même principe que 'login'. Deux routes distinctes (pas de
        // segment parent avec redirectTo) : /catalogue n'a aucune capacité
        // commune à protéger, contrairement à /staff ou /admin.
        path: 'catalogue',
        loadComponent: () => import('./catalogue/pages/catalogue-page/catalogue-page').then((m) => m.CataloguePage),
      },
      {
        path: 'catalogue/:id',
        loadComponent: () =>
          import('./catalogue/pages/title-detail-page/title-detail-page').then((m) => m.TitleDetailPage),
      },
      {
        
        // (Article.articleStatus = PUBLISHED uniquement) — aucun guard,
        // même principe exact que 'catalogue'/'catalogue/:id'. Deux routes
        // distinctes (pas de segment parent avec redirectTo), même
        // raisonnement que Catalogue.
        path: 'articles',
        loadComponent: () =>
          import('./articles/pages/article-list-page/article-list-page').then((m) => m.ArticleListPage),
      },
      {
        path: 'articles/:slug',
        loadComponent: () =>
          import('./articles/pages/article-detail-page/article-detail-page').then((m) => m.ArticleDetailPage),
      },
      {
        // Espace éditorial public consacré à Georges Lemaître : contenu
        // statique, aucun guard. Les dossiers détaillés partagent un même
        // composant de lecture piloté par la donnée de route `topic`.
        path: 'georges-lemaitre',
        loadComponent: () =>
          import('./shared/pages/georges-lemaitre/georges-lemaitre').then((m) => m.GeorgesLemaitrePage),
      },
      {
        path: 'georges-lemaitre/univers-en-expansion',
        data: { topic: 'univers-en-expansion' },
        loadComponent: () =>
          import('./shared/pages/georges-lemaitre-topic/georges-lemaitre-topic').then((m) => m.GeorgesLemaitreTopicPage),
      },
      {
        path: 'georges-lemaitre/atome-primitif',
        data: { topic: 'atome-primitif' },
        loadComponent: () =>
          import('./shared/pages/georges-lemaitre-topic/georges-lemaitre-topic').then((m) => m.GeorgesLemaitreTopicPage),
      },
      {
        path: 'georges-lemaitre/charleroi',
        data: { topic: 'charleroi' },
        loadComponent: () =>
          import('./shared/pages/georges-lemaitre-topic/georges-lemaitre-topic').then((m) => m.GeorgesLemaitreTopicPage),
      },
      {
        path: 'georges-lemaitre/pourquoi-primatis',
        data: { topic: 'pourquoi-primatis' },
        loadComponent: () =>
          import('./shared/pages/georges-lemaitre-topic/georges-lemaitre-topic').then((m) => m.GeorgesLemaitreTopicPage),
      },
      {
        path: 'georges-lemaitre/parcours',
        data: { topic: 'parcours' },
        loadComponent: () =>
          import('./shared/pages/georges-lemaitre-topic/georges-lemaitre-topic').then((m) => m.GeorgesLemaitreTopicPage),
      },
      {
        path: 'georges-lemaitre/distinctions',
        data: { topic: 'distinctions' },
        loadComponent: () =>
          import('./shared/pages/georges-lemaitre-topic/georges-lemaitre-topic').then((m) => m.GeorgesLemaitreTopicPage),
      },
    ],
  },
  {
    
    // enfant comme permissionGuard en Staff/Admin : ROLE_MEMBER conditionne
    // TOUTE la zone /member par définition ("Espace membre"), pas une
    // ressource spécifique parmi plusieurs possibles. Backend /api/v1/me/**
    // reste inchangé (ownership structurelle, accessible à tout AppUser
    // authentifié) — cette restriction est strictement UX/frontend.
    path: 'member',
    component: MemberLayout,
    canActivate: [authGuard, roleGuard],
    data: { roles: ['ROLE_MEMBER'] },
    children: [
      { path: '', redirectTo: 'profile', pathMatch: 'full' },
      {
        path: 'profile',
        loadComponent: () =>
          import('./member/profile/pages/member-profile-page/member-profile-page').then(
            (m) => m.MemberProfilePage,
          ),
      },
      {
        
        // aucun guard propre — hérite du roleGuard ROLE_MEMBER porté par la
        // zone /member (même principe que 'profile').
        path: 'loans',
        loadComponent: () =>
          import('./member/loans/pages/member-loans-page/member-loans-page').then((m) => m.MemberLoansPage),
      },
      {
        
        // réservations du membre authentifié, aucun guard propre — hérite du
        // roleGuard ROLE_MEMBER porté par la zone /member (même principe
        // exact que 'loans'). Aucune permission RESERVATION_READ ici :
        // l'ownership self-service passe uniquement par l'identité JWT
        // (endpoints /me/**), jamais par une permission frontend
        
        path: 'reservations',
        loadComponent: () =>
          import('./member/reservations/pages/member-reservations-page/member-reservations-page').then(
            (m) => m.MemberReservationsPage,
          ),
      },
      {
        
        // aucun guard propre — hérite du roleGuard ROLE_MEMBER porté par la
        // zone /member (même principe exact que 'loans'/'reservations').
        // Aucune permission FINE_READ ici : ownership self-service par
        // identité JWT uniquement (endpoint /me/fines), aucune action
        // (paiement/annulation requièrent toutes deux FINE_MANAGE, staff
        
        path: 'fines',
        loadComponent: () =>
          import('./member/fines/pages/member-fines-page/member-fines-page').then((m) => m.MemberFinesPage),
      },
      {
        
        // des Notifications du membre authentifié, aucun guard propre —
        // hérite du roleGuard ROLE_MEMBER porté par la zone /member (même
        // principe exact que 'loans'/'reservations'/'fines'). Aucune
        // permission NOTIFICATION_READ/NOTIFICATION_MANAGE : ownership
        // self-service par identité JWT uniquement (endpoints /me/notifications).
        path: 'notifications',
        loadComponent: () =>
          import('./member/notifications/pages/member-notifications-page/member-notifications-page').then(
            (m) => m.MemberNotificationsPage,
          ),
      },
    ],
  },
  {
    
    
    // de route, une seule déclaration pour les deux pages.
    path: 'staff',
    component: StaffLayout,
    canActivate: [authGuard],
    children: [
      { path: '', redirectTo: 'users', pathMatch: 'full' },
      {
        path: 'users',
        canActivate: [permissionGuard],
        data: { permissions: ['USER_READ'] },
        children: [
          {
            path: '',
            loadComponent: () =>
              import('./staff/users/pages/staff-users-page/staff-users-page').then((m) => m.StaffUsersPage),
          },
          {
            path: ':id',
            loadComponent: () =>
              import('./staff/users/pages/staff-user-detail-page/staff-user-detail-page').then(
                (m) => m.StaffUserDetailPage,
              ),
          },
        ],
      },
      {
        
        // 'users', même structure que /admin/users : 'new' déclaré avant
        // ':id' pour que le routing statique l'emporte sur le paramétrique.
        
        // le détail Title.
        path: 'catalogue',
        canActivate: [permissionGuard],
        data: { permissions: ['CATALOGUE_MANAGE'] },
        children: [
          {
            path: '',
            loadComponent: () =>
              import('./staff/catalogue/pages/staff-catalogue-page/staff-catalogue-page').then(
                (m) => m.StaffCataloguePage,
              ),
          },
          {
            path: 'new',
            loadComponent: () =>
              import('./staff/catalogue/pages/staff-title-create-page/staff-title-create-page').then(
                (m) => m.StaffTitleCreatePage,
              ),
          },
          {
            path: ':id',
            loadComponent: () =>
              import('./staff/catalogue/pages/staff-title-detail-page/staff-title-detail-page').then(
                (m) => m.StaffTitleDetailPage,
              ),
          },
        ],
      },
      {
        
        // 'users'/'catalogue', même structure : 'new' et 'tags' déclarés
        // avant ':id' pour que le routing statique l'emporte sur le
        // paramétrique (même précédent que 'catalogue'). 'tags' hérite du
        // même guard/permission ARTICLE_MANAGE que le reste du segment —
        
        
        
        // Copy-like séparée n'est nécessaire ici (Tags gérés inline dans
        // le détail Article via association, sauf pour leur CRUD propre
        // qui vit sur cette route dédiée).
        path: 'articles',
        canActivate: [permissionGuard],
        data: { permissions: ['ARTICLE_MANAGE'] },
        children: [
          {
            path: '',
            loadComponent: () =>
              import('./staff/articles/pages/staff-articles-page/staff-articles-page').then(
                (m) => m.StaffArticlesPage,
              ),
          },
          {
            path: 'new',
            loadComponent: () =>
              import('./staff/articles/pages/staff-article-create-page/staff-article-create-page').then(
                (m) => m.StaffArticleCreatePage,
              ),
          },
          {
            path: 'tags',
            loadComponent: () =>
              import('./staff/articles/pages/staff-tags-page/staff-tags-page').then((m) => m.StaffTagsPage),
          },
          {
            path: ':id',
            loadComponent: () =>
              import('./staff/articles/pages/staff-article-detail-page/staff-article-detail-page').then(
                (m) => m.StaffArticleDetailPage,
              ),
          },
        ],
      },
      {
        
        // 'catalogue', même structure. Un seul écran (liste + retour) :
        
        // ':id' comme pour users/catalogue.
        path: 'loans',
        canActivate: [permissionGuard],
        data: { permissions: ['LOAN_READ'] },
        children: [
          {
            path: '',
            loadComponent: () =>
              import('./staff/loans/pages/staff-loans-page/staff-loans-page').then((m) => m.StaffLoansPage),
          },
        ],
      },
      {
        
        // 'users'/'catalogue'/'loans', même structure : la route ne
        // demande que la consultation, l'action mutatrice (annulation,
        // création) est déjà contrôlée dans StaffReservationsPage via
        
        // jamais au niveau du guard de route. Un seul écran (liste +
        // annulation + création) : aucun GET /api/v1/reservations/{id}
        
        path: 'reservations',
        canActivate: [permissionGuard],
        data: { permissions: ['RESERVATION_READ'] },
        children: [
          {
            path: '',
            loadComponent: () =>
              import('./staff/reservations/pages/staff-reservations-page/staff-reservations-page').then(
                (m) => m.StaffReservationsPage,
              ),
          },
        ],
      },
      {
        
        // 'users'/'catalogue'/'loans'/'reservations', même structure : la
        // route ne demande que la consultation, les actions mutatrices
        // (confirmation de paiement, annulation) sont déjà contrôlées dans
        // StaffFinesPage via AuthService.hasPermission('FINE_MANAGE'),
        // jamais au niveau du guard de route. Un seul écran (liste +
        // actions) : aucun GET /api/v1/fines/{id}, aucune route ':id',
        // aucune création (Fine créée automatiquement au retour tardif,
        
        // 'loans'/'reservations'.
        path: 'fines',
        canActivate: [permissionGuard],
        data: { permissions: ['FINE_READ'] },
        children: [
          {
            path: '',
            loadComponent: () =>
              import('./staff/fines/pages/staff-fines-page/staff-fines-page').then((m) => m.StaffFinesPage),
          },
        ],
      },
    ],
  },
  {
    
    
    // ET détail par héritage de route, une seule déclaration pour les
    // trois pages. 'new' déclaré avant ':id' pour que le routing statique
    // l'emporte sur le paramétrique.
    path: 'admin',
    component: AdminLayout,
    canActivate: [authGuard],
    children: [
      { path: '', redirectTo: 'users', pathMatch: 'full' },
      {
        path: 'users',
        canActivate: [permissionGuard],
        data: { permissions: ['USER_MANAGE'] },
        children: [
          {
            path: '',
            loadComponent: () =>
              import('./admin/users/pages/admin-users-page/admin-users-page').then((m) => m.AdminUsersPage),
          },
          {
            path: 'new',
            loadComponent: () =>
              import('./admin/users/pages/admin-user-create-page/admin-user-create-page').then(
                (m) => m.AdminUserCreatePage,
              ),
          },
          {
            path: ':id',
            loadComponent: () =>
              import('./admin/users/pages/admin-user-detail-page/admin-user-detail-page').then(
                (m) => m.AdminUserDetailPage,
              ),
          },
        ],
      },
      {
        
        // même structure (segment guardé par permissionGuard, un seul
        // enfant liste). SETTING_READ pilote la route, comme pour
        // /staff/loans|reservations|fines : SETTING_MANAGE n'est vérifiée
        // qu'à l'intérieur d'AdminSettingsPage (bouton "Modifier"), jamais
        // au niveau du guard de route. Un seul écran (liste + modification
        // de valeur via dialog) : aucune création/suppression de clé,
        
        path: 'settings',
        canActivate: [permissionGuard],
        data: { permissions: ['SETTING_READ'] },
        children: [
          {
            path: '',
            loadComponent: () =>
              import('./admin/settings/pages/admin-settings-page/admin-settings-page').then(
                (m) => m.AdminSettingsPage,
              ),
          },
        ],
      },
    ],
  },
  {
    path: 'forbidden',
    component: Forbidden,
  },
  {
    path: '**',
    component: NotFound,
  },
];
