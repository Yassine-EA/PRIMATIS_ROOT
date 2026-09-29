package be.primatis.access;

/**
 * Projection associant un rôle à une permission dans le modèle RBAC.
 *
 * Le code de permission peut être absent lorsqu'un rôle ne possède
 * aucune permission associée.
 */
public record RoleAndPermissionCode(String roleCode, String permissionCode) {
}
