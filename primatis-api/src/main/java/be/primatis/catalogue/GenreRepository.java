package be.primatis.catalogue;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Accès aux données des genres.
 */
public interface GenreRepository extends JpaRepository<Genre, Long> {

    /**
     * Recherche un genre à partir de son code.
     *
     * @param code code du genre
     * @return genre correspondant, s'il existe
     */
    Optional<Genre> findByCode(String code);

    /**
     * Vérifie si un genre existe déjà avec le code fourni.
     *
     * @param code code du genre
     * @return true si un genre existe avec ce code
     */
    boolean existsByCode(String code);

    /**
     * Vérifie si un genre existe déjà avec le libellé fourni.
     *
     * @param label libellé du genre
     * @return true si un genre existe avec ce libellé
     */
    boolean existsByLabel(String label);

    /**
     * Retourne les genres associés à un titre.
     *
     * @param titleId identifiant du titre
     * @return liste des genres associés au titre
     */
    @Query("SELECT tg.genre FROM TitleGenre tg WHERE tg.title.id = :titleId "
            + "ORDER BY tg.genre.label ASC, tg.genre.id ASC")
    List<Genre> findByTitleIdOrderByLabelAscIdAsc(
            @Param("titleId") Long titleId);
}