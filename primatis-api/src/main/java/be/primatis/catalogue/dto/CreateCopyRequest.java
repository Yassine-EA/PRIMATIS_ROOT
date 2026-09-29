package be.primatis.catalogue.dto;

import be.primatis.catalogue.AvailabilityStatus;
import be.primatis.catalogue.CopyCondition;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Requête de création d'un exemplaire.
 */
public record CreateCopyRequest(
                @NotBlank String inventoryCode,
                String location,
                @NotNull CopyCondition copyCondition,
                @NotNull AvailabilityStatus availabilityStatus) {
}