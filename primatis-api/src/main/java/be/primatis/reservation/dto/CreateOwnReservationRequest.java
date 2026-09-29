package be.primatis.reservation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
/**
 * Requête de création d'une réservation pour l'utilisateur authentifié.
 */
public record CreateOwnReservationRequest(@NotNull @Positive Long titleId) {
}
