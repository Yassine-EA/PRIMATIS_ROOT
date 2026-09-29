package be.primatis.article.dto;

import be.primatis.article.Article;
import be.primatis.article.ArticleStatus;

import java.time.Instant;
import java.util.Objects;

/**
 * Représentation synthétique d'un article destinée à la gestion par le
 * personnel.
 */
public record StaffArticleSummaryResponse(
        Long id,
        String title,
        String summary,
        String slug,
        ArticleStatus articleStatus,
        ArticleUserResponse author,
        Instant publishedAt,
        Instant updatedAt) {

    /**
     * Crée la représentation synthétique d'un article pour le personnel.
     *
     * @param article article à représenter
     * @return représentation synthétique de l'article
     */
    public static StaffArticleSummaryResponse from(Article article) {
        Objects.requireNonNull(article, "article");
        return new StaffArticleSummaryResponse(
                article.getId(),
                article.getTitle(),
                article.getSummary(),
                article.getSlug(),
                article.getArticleStatus(),
                ArticleUserResponse.from(article.getAuthorUser()),
                article.getPublishedAt(),
                article.getUpdatedAt());
    }
}
