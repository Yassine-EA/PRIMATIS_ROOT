package be.primatis.fine.dto;

import be.primatis.catalogue.Copy;

import java.util.Objects;


/**
 * Représentation synthétique de l'exemplaire associé à une amende.
 */
public record FineCopyResponse(Long id, String inventoryCode, Long titleId, String coverImageUrl) {

    /**
     * Crée la représentation de l'exemplaire.
     *
     * @param copy exemplaire à représenter
     * @return représentation de l'exemplaire
     */
    public static FineCopyResponse from(Copy copy) {
        Objects.requireNonNull(copy, "copy");
        return new FineCopyResponse(copy.getId(), copy.getInventoryCode(), copy.getTitle().getId(),
                copy.getTitle().getCoverImageUrl());
    }
}
