package be.primatis.catalogue.dto;

import be.primatis.catalogue.TitleStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Requête de modification du statut d'un titre.
 */
public record UpdateTitleStatusRequest(
        @NotNull TitleStatus status) {
}