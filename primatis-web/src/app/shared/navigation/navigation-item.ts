import { PermissionCode } from '../../auth/models/permission';
import { RoleCode } from '../../auth/models/role';

export interface NavigationItem {
  readonly label: string;
  readonly routerLink: string;
  readonly requiredPermissions?: readonly PermissionCode[];
  readonly requiredRoles?: readonly RoleCode[];
  
  readonly icon?: string;
}


export function isNavigationItemVisible(
  item: NavigationItem,
  authenticated: boolean,
  grantedRoles: readonly string[],
  grantedPermissions: readonly string[],
): boolean {
  const hasPermissionConstraint = (item.requiredPermissions?.length ?? 0) > 0;
  const hasRoleConstraint = (item.requiredRoles?.length ?? 0) > 0;

  if (!hasPermissionConstraint && !hasRoleConstraint) {
    return true;
  }
  if (!authenticated) {
    return false;
  }
  if (
    hasPermissionConstraint &&
    !item.requiredPermissions!.every((permission) => grantedPermissions.includes(permission))
  ) {
    return false;
  }
  if (hasRoleConstraint && !item.requiredRoles!.some((role) => grantedRoles.includes(role))) {
    return false;
  }
  return true;
}
