package be.primatis.e2e.web;

import jakarta.validation.constraints.NotNull;

/**
 * Requête de décalage de l'horloge E2E.
 */
public record AdvanceTimeRequest(@NotNull Long hours) {
}
