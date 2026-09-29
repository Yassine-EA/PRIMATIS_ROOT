package be.primatis.catalogue;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/**
 * Accès aux données des titres du catalogue.
 */
public interface TitleRepository extends JpaRepository<Title, Long>, JpaSpecificationExecutor<Title> {

    /**
     * Recherche un titre à partir de son ISBN.
     *
     * @param isbn ISBN du titre
     * @return titre correspondant, s'il existe
     */
    Optional<Title> findByIsbn(String isbn);

    /**
     * Vérifie si un titre existe déjà avec l'ISBN fourni.
     *
     * @param isbn ISBN du titre
     * @return true si un titre existe avec cet ISBN
     */
    boolean existsByIsbn(String isbn);
}