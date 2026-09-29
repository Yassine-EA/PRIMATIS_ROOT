package be.primatis.loan.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
/**
 * Requête de création d'un prêt.
 */
public record CreateLoanRequest(
        @NotNull @Positive Long borrowerUserId,
        @NotNull @Positive Long copyId) {
}
