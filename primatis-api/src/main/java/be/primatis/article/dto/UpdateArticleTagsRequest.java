package be.primatis.article.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Requête de modification des tags associés à un article.
 *
 * La liste fournie représente l'ensemble final des tags à associer.
 */
public record UpdateArticleTagsRequest(@NotNull List<Long> tagIds) {
}
