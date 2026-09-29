package be.primatis.reservation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
/**
 * Requête de création d'une réservation par le personnel.
 */
public record CreateReservationRequest(
        @NotNull @Positive Long userId,
        @NotNull @Positive Long titleId) {
}
