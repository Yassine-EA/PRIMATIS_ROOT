package be.primatis.reservation.dto;

import be.primatis.user.AppUser;

import java.util.Objects;
/**
 * Représentation synthétique du membre associé à une réservation.
 */
public record ReservationMemberResponse(Long id, String memberNumber, String firstName, String lastName) {

    /**
     * Crée la représentation membre.
     *
     * @param appUser membre à représenter
     * @return représentation membre
     */
    public static ReservationMemberResponse from(AppUser appUser) {
        Objects.requireNonNull(appUser, "appUser");
        return new ReservationMemberResponse(
                appUser.getId(), appUser.getMemberNumber(), appUser.getFirstName(), appUser.getLastName());
    }
}
