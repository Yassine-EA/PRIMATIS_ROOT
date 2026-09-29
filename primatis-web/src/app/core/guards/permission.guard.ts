import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthService } from '../../auth/services/auth.service';
import { PermissionCode } from '../../auth/models/permission';


export const permissionGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (!authService.authenticated()) {
    return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
  }

  const requiredPermissions = route.data['permissions'];
  if (!isPermissionCodeList(requiredPermissions)) {
    return router.createUrlTree(['/forbidden']);
  }

  const grantedPermissions = authService.permissions();
  const hasAllRequiredPermissions = requiredPermissions.every((permission) =>
    grantedPermissions.includes(permission),
  );

  return hasAllRequiredPermissions ? true : router.createUrlTree(['/forbidden']);
};

function isPermissionCodeList(value: unknown): value is PermissionCode[] {
  return Array.isArray(value) && value.length > 0 && value.every((item) => typeof item === 'string');
}
