package be.primatis.access;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Accès aux données des rôles.
 */

public interface RoleRepository extends JpaRepository<Role, Long> {

    /**
     * Recherche un rôle à partir de son code.
     *
     * @param code code du rôle
     * @return rôle correspondant, s'il existe
     */
    Optional<Role> findByCode(String code);
}
