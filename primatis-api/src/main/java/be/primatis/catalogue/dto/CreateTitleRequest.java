package be.primatis.catalogue.dto;

import be.primatis.catalogue.Language;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

/**
 * Requête de création d'un titre.
 */
public record CreateTitleRequest(
                String isbn,
                @NotBlank String title,
                String subtitle,
                String summary,
                Integer publicationYear,
                @NotNull Language language,
                @Positive Integer pageCount,
                String publisher,
                String coverImageUrl,
                @NotEmpty List<Long> authorIds,
                List<Long> genreIds) {
}