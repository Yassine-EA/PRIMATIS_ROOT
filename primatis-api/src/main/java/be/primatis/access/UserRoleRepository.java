package be.primatis.access;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Accès aux attributions de rôles des utilisateurs.
 */
public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {

    /**
     * Retourne les rôles attribués à un utilisateur.
     *
     * @param userId identifiant de l'utilisateur
     * @return liste des attributions de rôles
     */
    List<UserRole> findByIdUserId(Long userId);
}
