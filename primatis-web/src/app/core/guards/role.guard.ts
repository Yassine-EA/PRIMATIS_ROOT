import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthService } from '../../auth/services/auth.service';
import { RoleCode } from '../../auth/models/role';


export const roleGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (!authService.authenticated()) {
    return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
  }

  const requiredRoles = route.data['roles'];
  if (!isRoleCodeList(requiredRoles)) {
    return router.createUrlTree(['/forbidden']);
  }

  const grantedRoles = authService.roles();
  const hasAnyRequiredRole = requiredRoles.some((role) => grantedRoles.includes(role));

  return hasAnyRequiredRole ? true : router.createUrlTree(['/forbidden']);
};

function isRoleCodeList(value: unknown): value is RoleCode[] {
  return Array.isArray(value) && value.length > 0 && value.every((item) => typeof item === 'string');
}
