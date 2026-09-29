package be.primatis.user.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
/**
 * Requête de définition ou de remplacement d'une résidence.
 */
public record UpdateResidenceRequest(
        @NotNull Long cityId,
        @NotBlank @Size(max = 255) String street,
        @NotBlank @Size(max = 20) String streetNumber,
        @Size(max = 20) String boxNumber,
        @Size(max = 255) String additionalInfo) {
}
