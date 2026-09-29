package be.primatis.catalogue.dto;

import be.primatis.catalogue.Genre;

import java.util.Objects;

/**
 * Représentation d'un genre exposée par l'API.
 */
public record GenreResponse(
        Long id,
        String code,
        String label,
        String description) {

    /**
     * Crée la représentation d'un genre.
     *
     * @param genre genre à représenter
     * @return représentation du genre
     */
    public static GenreResponse from(Genre genre) {
        Objects.requireNonNull(genre, "genre");

        return new GenreResponse(
                genre.getId(),
                genre.getCode(),
                genre.getLabel(),
                genre.getDescription());
    }
}