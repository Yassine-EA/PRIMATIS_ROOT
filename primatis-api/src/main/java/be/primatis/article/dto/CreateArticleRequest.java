package be.primatis.article.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Requête de création d'un article.
 */
public record CreateArticleRequest(@NotBlank @Size(max = 255) String title, @NotBlank String content, String summary) {
}
