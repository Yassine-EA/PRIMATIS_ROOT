package be.primatis.loan.dto;

import be.primatis.user.AppUser;

import java.util.Objects;
/**
 * Représentation synthétique de l'emprunteur associé à un prêt.
 */
public record LoanBorrowerResponse(Long id, String memberNumber, String firstName, String lastName) {

    /**
     * Crée la représentation synthétique d'un emprunteur.
     *
     * @param appUser utilisateur à représenter
     * @return représentation de l'emprunteur
     */
    public static LoanBorrowerResponse from(AppUser appUser) {
        Objects.requireNonNull(appUser, "appUser");
        return new LoanBorrowerResponse(
                appUser.getId(), appUser.getMemberNumber(), appUser.getFirstName(), appUser.getLastName());
    }
}
