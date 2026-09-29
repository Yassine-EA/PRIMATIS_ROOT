package be.primatis.catalogue;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/**
 * Accès aux associations entre les titres et les auteurs.
 */
public interface TitleAuthorRepository extends JpaRepository<TitleAuthor, TitleAuthorId> {

    /**
     * Retourne les associations d'auteurs d'un titre.
     *
     * @param titleId identifiant du titre
     * @return liste des associations d'auteurs
     */
    List<TitleAuthor> findByIdTitleId(Long titleId);

    /**
     * Projection du nom d'un auteur associé à un titre.
     */
    interface TitleAuthorName {

        Long getTitleId();

        String getFullName();
    }

    /**
     * Retourne les noms des auteurs associés à plusieurs titres.
     *
     * @param titleIds identifiants des titres
     * @return noms des auteurs regroupés par titre
     */
    @Query("SELECT ta.title.id AS titleId, ta.author.fullName AS fullName FROM TitleAuthor ta "
            + "WHERE ta.title.id IN :titleIds "
            + "ORDER BY ta.title.id ASC, ta.author.fullName ASC, ta.author.id ASC")
    List<TitleAuthorName> findAuthorNamesByTitleIds(
            @Param("titleIds") Collection<Long> titleIds);
}