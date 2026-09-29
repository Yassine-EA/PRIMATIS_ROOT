package be.primatis.catalogue.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Requête de création d'un genre.
 */
public record CreateGenreRequest(
        @NotBlank String code,
        @NotBlank String label,
        String description) {
}