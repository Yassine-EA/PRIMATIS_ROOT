package be.primatis.catalogue.dto;

import be.primatis.catalogue.Language;
import be.primatis.catalogue.Title;
import be.primatis.catalogue.TitleStatus;

import java.util.List;
import java.util.Objects;

/**
 * Représentation synthétique d'un titre destinée aux listes et recherches du
 * catalogue.
 */
public record TitleResponse(
        Long id,
        String isbn,
        String title,
        String subtitle,
        Integer publicationYear,
        Language language,
        String publisher,
        String coverImageUrl,
        TitleStatus titleStatus,
        List<String> authorNames,
        long availableCopies) {

    /**
     * Crée la représentation synthétique d'un titre.
     *
     * @param title           titre à représenter
     * @param authorNames     noms des auteurs associés
     * @param availableCopies nombre d'exemplaires actuellement disponibles
     * @return représentation synthétique du titre
     */
    public static TitleResponse from(
            Title title,
            List<String> authorNames,
            long availableCopies) {

        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(authorNames, "authorNames");

        return new TitleResponse(
                title.getId(),
                title.getIsbn(),
                title.getTitle(),
                title.getSubtitle(),
                title.getPublicationYear(),
                title.getLanguage(),
                title.getPublisher(),
                title.getCoverImageUrl(),
                title.getTitleStatus(),
                List.copyOf(authorNames),
                availableCopies);
    }
}