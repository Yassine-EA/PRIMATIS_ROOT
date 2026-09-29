package be.primatis.catalogue.dto;

import be.primatis.catalogue.Author;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Représentation d'un auteur exposée par l'API.
 */
public record AuthorResponse(
        Long id,
        String fullName,
        LocalDate birthDate,
        LocalDate deathDate,
        String nationality,
        String biography) {

    /**
     * Crée la représentation d'un auteur.
     *
     * @param author auteur à représenter
     * @return représentation de l'auteur
     */
    public static AuthorResponse from(Author author) {
        Objects.requireNonNull(author, "author");

        return new AuthorResponse(
                author.getId(),
                author.getFullName(),
                author.getBirthDate(),
                author.getDeathDate(),
                author.getNationality(),
                author.getBiography());
    }
}