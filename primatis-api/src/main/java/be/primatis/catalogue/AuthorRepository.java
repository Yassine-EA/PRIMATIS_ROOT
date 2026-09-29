package be.primatis.catalogue;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Accès aux données des auteurs.
 */
public interface AuthorRepository extends JpaRepository<Author, Long> {

        /**
         * Recherche les auteurs dont le nom contient le texte fourni,
         * sans tenir compte de la casse.
         *
         * @param fullName texte recherché dans le nom de l'auteur
         * @param pageable paramètres de pagination
         * @return page des auteurs correspondants
         */
        Page<Author> findByFullNameContainingIgnoreCase(
                        String fullName,
                        Pageable pageable);

        /**
         * Retourne les auteurs associés à un titre.
         *
         * @param titleId identifiant du titre
         * @return liste des auteurs associés au titre
         */
        @Query("SELECT ta.author FROM TitleAuthor ta WHERE ta.title.id = :titleId "
                        + "ORDER BY ta.author.fullName ASC, ta.author.id ASC")
        List<Author> findByTitleIdOrderByFullNameAscIdAsc(
                        @Param("titleId") Long titleId);
}