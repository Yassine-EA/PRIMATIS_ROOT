package be.primatis.article;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Accès aux données des articles.
 */

public interface ArticleRepository extends JpaRepository<Article, Long>, JpaSpecificationExecutor<Article> {

    /**
     * Recherche un article à partir de son slug.
     *
     * @param slug slug de l'article
     * @return article correspondant, s'il existe
     */
    Optional<Article> findBySlug(String slug);

    /**
     * Retourne une page d'articles correspondant à un statut donné.
     *
     * @param articleStatus statut des articles recherchés
     * @param pageable      paramètres de pagination
     * @return page d'articles correspondante
     */
    @EntityGraph(attributePaths = {"authorUser"})
    Page<Article> findByArticleStatus(ArticleStatus articleStatus, Pageable pageable);

    /**
     * Recherche un article à partir de son slug et de son statut.
     *
     * @param slug          slug de l'article
     * @param articleStatus statut attendu
     * @return article correspondant, s'il existe
     */
    @EntityGraph(attributePaths = {"authorUser", "lastModifiedByUser"})
    Optional<Article> findBySlugAndArticleStatus(String slug, ArticleStatus articleStatus);

    /**
     * Retourne les tags associés à un article.
     *
     * @param articleId identifiant de l'article
     * @return liste des tags associés
     */
    @Query("SELECT at.tag FROM ArticleTag at WHERE at.article.id = :articleId ORDER BY at.tag.id ASC")
    List<Tag> findTagsByArticleId(@Param("articleId") Long articleId);

    /**
     * Recherche un article par son identifiant en appliquant un verrou d'écriture.
     *
     * @param id identifiant de l'article
     * @return article correspondant, s'il existe
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Article a WHERE a.id = :id")
    Optional<Article> findByIdForUpdate(@Param("id") Long id);

    /**
     * Retourne les articles destinés à la gestion par le personnel,
     * du plus récemment modifié au plus ancien.
     *
     * @param pageable paramètres de pagination
     * @return page d'articles
     */
    @EntityGraph(attributePaths = {"authorUser"})
    Page<Article> findAllByOrderByUpdatedAtDescIdDesc(Pageable pageable);

    /**
     * Retourne une page d'articles correspondant aux critères de recherche fournis.
     *
     * @param spec     critères de recherche
     * @param pageable paramètres de pagination
     * @return page d'articles correspondante
     */
    @Override
    @EntityGraph(attributePaths = {"authorUser"})
    Page<Article> findAll(Specification<Article> spec, Pageable pageable);

    /**
     * Recherche un article avec les utilisateurs qui lui sont associés.
     *
     * @param id identifiant de l'article
     * @return article correspondant, s'il existe
     */
    @EntityGraph(attributePaths = {"authorUser", "lastModifiedByUser"})
    @Query("SELECT a FROM Article a WHERE a.id = :id")
    Optional<Article> findByIdWithUsers(@Param("id") Long id);
}
