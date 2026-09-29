package be.primatis.reservation.dto;

import be.primatis.catalogue.Copy;

import java.util.Objects;
/**
 * Représentation synthétique de l'exemplaire affecté à une réservation.
 */
public record ReservationCopyResponse(Long id, String inventoryCode, Long titleId) {

    /**
     * Crée la représentation exemplaire.
     *
     * @param copy exemplaire à représenter
     * @return représentation exemplaire
     */
    public static ReservationCopyResponse from(Copy copy) {
        Objects.requireNonNull(copy, "copy");
        return new ReservationCopyResponse(copy.getId(), copy.getInventoryCode(), copy.getTitle().getId());
    }
}
