package be.primatis.catalogue.dto;

import be.primatis.catalogue.Author;
import be.primatis.catalogue.Genre;
import be.primatis.catalogue.Language;
import be.primatis.catalogue.Title;
import be.primatis.catalogue.TitleStatus;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Représentation détaillée d'un titre exposée par l'API.
 */
public record TitleDetailResponse(
        Long id,
        String isbn,
        String title,
        String subtitle,
        String summary,
        Integer publicationYear,
        Language language,
        Integer pageCount,
        String publisher,
        String coverImageUrl,
        TitleStatus titleStatus,
        List<AuthorResponse> authors,
        List<GenreResponse> genres,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Crée la représentation détaillée d'un titre avec ses auteurs et ses genres.
     *
     * @param title   titre à représenter
     * @param authors auteurs associés au titre
     * @param genres  genres associés au titre
     * @return représentation détaillée du titre
     */
    public static TitleDetailResponse from(
            Title title,
            List<Author> authors,
            List<Genre> genres) {

        Objects.requireNonNull(title, "title");

        return new TitleDetailResponse(
                title.getId(),
                title.getIsbn(),
                title.getTitle(),
                title.getSubtitle(),
                title.getSummary(),
                title.getPublicationYear(),
                title.getLanguage(),
                title.getPageCount(),
                title.getPublisher(),
                title.getCoverImageUrl(),
                title.getTitleStatus(),
                authors == null
                        ? List.of()
                        : authors.stream().map(AuthorResponse::from).toList(),
                genres == null
                        ? List.of()
                        : genres.stream().map(GenreResponse::from).toList(),
                title.getCreatedAt(),
                title.getUpdatedAt());
    }
}