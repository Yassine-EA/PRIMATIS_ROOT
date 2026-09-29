package be.primatis.article.dto;

import be.primatis.article.Article;
import be.primatis.article.ArticleStatus;
import be.primatis.article.Tag;
import be.primatis.user.AppUser;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Représentation complète d'un article exposée par l'API.
 *
 * Les informations sur l'auteur, le dernier utilisateur ayant modifié
 * l'article et les tags associés sont incluses dans la réponse.
 */
public record ArticleResponse(
        Long id,
        String title,
        String content,
        String summary,
        String slug,
        ArticleStatus articleStatus,
        ArticleUserResponse author,
        ArticleUserResponse lastModifiedBy,
        List<TagResponse> tags,
        Instant publishedAt,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Crée la représentation d'un article avec ses tags associés.
     *
     * @param article article à représenter
     * @param tags    tags associés à l'article
     * @return représentation de l'article
     */
    public static ArticleResponse from(Article article, List<Tag> tags) {
        Objects.requireNonNull(article, "article");

        AppUser lastModifiedByUser = article.getLastModifiedByUser();

        return new ArticleResponse(
                article.getId(),
                article.getTitle(),
                article.getContent(),
                article.getSummary(),
                article.getSlug(),
                article.getArticleStatus(),
                ArticleUserResponse.from(article.getAuthorUser()),
                lastModifiedByUser == null ? null : ArticleUserResponse.from(lastModifiedByUser),
                tags == null ? List.of() : tags.stream().map(TagResponse::from).toList(),
                article.getPublishedAt(),
                article.getCreatedAt(),
                article.getUpdatedAt());
    }
}
