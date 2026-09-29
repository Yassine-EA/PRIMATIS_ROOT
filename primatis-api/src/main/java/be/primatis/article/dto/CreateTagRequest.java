package be.primatis.article.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Requête de création d'un tag.
 */
public record CreateTagRequest(
                @NotBlank @Size(max = 50) String code,
                @NotBlank @Size(max = 100) String label,
                @Size(max = 255) String description) {
}
