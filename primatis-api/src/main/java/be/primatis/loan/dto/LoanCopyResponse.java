package be.primatis.loan.dto;

import be.primatis.catalogue.Copy;

import java.util.Objects;
/**
 * Représentation synthétique de l'exemplaire associé à un prêt.
 */
public record LoanCopyResponse(Long id, String inventoryCode, Long titleId, String coverImageUrl) {

    /**
     * Crée la représentation synthétique d'un exemplaire.
     *
     * @param copy exemplaire à représenter
     * @return représentation de l'exemplaire
     */
    public static LoanCopyResponse from(Copy copy) {
        Objects.requireNonNull(copy, "copy");
        return new LoanCopyResponse(copy.getId(), copy.getInventoryCode(), copy.getTitle().getId(),
                copy.getTitle().getCoverImageUrl());
    }
}
