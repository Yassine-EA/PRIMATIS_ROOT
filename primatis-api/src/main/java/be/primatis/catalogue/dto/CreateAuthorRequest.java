package be.primatis.catalogue.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

/**
 * Requête de création d'un auteur.
 */
public record CreateAuthorRequest(
                @NotBlank String fullName,
                LocalDate birthDate,
                LocalDate deathDate,
                String nationality,
                String biography) {
}