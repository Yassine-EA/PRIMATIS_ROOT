package be.primatis.article.dto;

import be.primatis.article.Article;
import be.primatis.article.ArticleThumbnailExtractor;

import java.time.Instant;
import java.util.Objects;

/**
 * Représentation synthétique d'un article destinée aux listes publiques.
 *
 * Elle contient les informations nécessaires à l'affichage d'un article
 * sans exposer son contenu complet.
 */
public record ArticleSummaryResponse(
        Long id,
        String title,
        String summary,
        String slug,
        ArticleUserResponse author,
        Instant publishedAt,
        String imageUrl) {

    /**
     * Crée la représentation synthétique d'un article.
     *
     * @param article            article à représenter
     * @param thumbnailExtractor extracteur utilisé pour récupérer l'image de
     *                           l'article
     * @return représentation synthétique de l'article
     */
    public static ArticleSummaryResponse from(Article article, ArticleThumbnailExtractor thumbnailExtractor) {
        Objects.requireNonNull(article, "article");
        Objects.requireNonNull(thumbnailExtractor, "thumbnailExtractor");
        return new ArticleSummaryResponse(
                article.getId(),
                article.getTitle(),
                article.getSummary(),
                article.getSlug(),
                ArticleUserResponse.from(article.getAuthorUser()),
                article.getPublishedAt(),
                thumbnailExtractor.extractFirstImageUrl(article.getContent()));
    }
}
