package be.primatis.article;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Accès aux associations entre les articles et les tags.
 */
public interface ArticleTagRepository extends JpaRepository<ArticleTag, ArticleTagId> {

    /**
     * Retourne les associations de tags d'un article.
     *
     * @param articleId identifiant de l'article
     * @return liste des associations de tags
     */
    List<ArticleTag> findByIdArticleId(Long articleId);

    /**
     * Supprime toutes les associations de tags d'un article.
     *
     * @param articleId identifiant de l'article
     * @return nombre d'associations supprimées
     */
    long deleteByIdArticleId(Long articleId);
}
