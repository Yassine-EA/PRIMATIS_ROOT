package be.primatis.article;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Accès aux données des tags.
 */

public interface TagRepository extends JpaRepository<Tag, Long> {

    /**
     * Vérifie si un tag existe déjà avec le code fourni.
     *
     * @param code code du tag
     * @return true si un tag existe avec ce code
     */
    boolean existsByCode(String code);
}
