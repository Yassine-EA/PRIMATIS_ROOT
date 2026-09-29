package be.primatis.reservation.dto;

import be.primatis.catalogue.Title;

import java.util.Objects;
/**
 * Représentation synthétique du titre associé à une réservation.
 */
public record ReservationTitleResponse(Long id, String title, String isbn, String coverImageUrl) {

    /**
     * Crée la représentation titre.
     *
     * @param titleEntity titre à représenter
     * @return représentation titre
     */
    public static ReservationTitleResponse from(Title titleEntity) {
        Objects.requireNonNull(titleEntity, "titleEntity");
        return new ReservationTitleResponse(titleEntity.getId(), titleEntity.getTitle(), titleEntity.getIsbn(),
                titleEntity.getCoverImageUrl());
    }
}
