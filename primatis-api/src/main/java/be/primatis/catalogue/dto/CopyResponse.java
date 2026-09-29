package be.primatis.catalogue.dto;

import be.primatis.catalogue.AvailabilityStatus;
import be.primatis.catalogue.Copy;
import be.primatis.catalogue.CopyCondition;

import java.util.Objects;

/**
 * Représentation d'un exemplaire exposée par l'API.
 */
public record CopyResponse(
        Long id,
        Long titleId,
        String inventoryCode,
        String location,
        CopyCondition copyCondition,
        AvailabilityStatus availabilityStatus) {

    /**
     * Crée la représentation d'un exemplaire.
     *
     * @param copy exemplaire à représenter
     * @return représentation de l'exemplaire
     */
    public static CopyResponse from(Copy copy) {
        Objects.requireNonNull(copy, "copy");

        return new CopyResponse(
                copy.getId(),
                copy.getTitle().getId(),
                copy.getInventoryCode(),
                copy.getLocation(),
                copy.getCopyCondition(),
                copy.getAvailabilityStatus());
    }
}