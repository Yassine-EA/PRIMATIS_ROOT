package be.primatis.catalogue.dto;

import be.primatis.catalogue.AvailabilityStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Requête de modification manuelle de la disponibilité d'un exemplaire.
 */
public record UpdateCopyAvailabilityRequest(
        @NotNull AvailabilityStatus status) {
}