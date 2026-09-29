package be.primatis.access;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Accès aux données des permissions.
 */

public interface PermissionRepository extends JpaRepository<Permission, Long> {
}
