package be.primatis.catalogue;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Accès aux associations entre les titres et les genres.
 */
public interface TitleGenreRepository extends JpaRepository<TitleGenre, TitleGenreId> {

    /**
     * Retourne les associations de genres d'un titre.
     *
     * @param titleId identifiant du titre
     * @return liste des associations de genres
     */
    List<TitleGenre> findByIdTitleId(Long titleId);
}