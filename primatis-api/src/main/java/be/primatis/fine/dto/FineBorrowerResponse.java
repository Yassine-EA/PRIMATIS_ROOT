package be.primatis.fine.dto;

import be.primatis.user.AppUser;

import java.util.Objects;


/**
 * Représentation synthétique de l'emprunteur associé à une amende.
 */
public record FineBorrowerResponse(Long id, String memberNumber, String firstName, String lastName) {

    /**
     * Crée la représentation de l'emprunteur.
     *
     * @param appUser utilisateur à représenter
     * @return représentation de l'emprunteur
     */
    public static FineBorrowerResponse from(AppUser appUser) {
        Objects.requireNonNull(appUser, "appUser");
        return new FineBorrowerResponse(
                appUser.getId(), appUser.getMemberNumber(), appUser.getFirstName(), appUser.getLastName());
    }
}
