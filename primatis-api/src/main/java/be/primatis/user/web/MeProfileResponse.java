package be.primatis.user.web;

import be.primatis.user.AccountStatus;
import be.primatis.user.AppUser;
import be.primatis.user.MemberStatus;

import java.time.LocalDate;
/**
 * Représentation du profil personnel exposée par l'API.
 */
public record MeProfileResponse(
        Long id,
        String email,
        String firstName,
        String lastName,
        String phoneNumber,
        AccountStatus accountStatus,
        String memberNumber,
        MemberStatus memberStatus,
        LocalDate registrationDate,
        LocalDate memberExpirationDate,
        String blockedReason) {

    /**
     * Crée la représentation correspondante.
     *
     * @param appUser objet à représenter
     * @return représentation créée
     */
    public static MeProfileResponse from(AppUser appUser) {
        return new MeProfileResponse(
                appUser.getId(),
                appUser.getEmail(),
                appUser.getFirstName(),
                appUser.getLastName(),
                appUser.getPhoneNumber(),
                appUser.getAccountStatus(),
                appUser.getMemberNumber(),
                appUser.getMemberStatus(),
                appUser.getRegistrationDate(),
                appUser.getMemberExpirationDate(),
                appUser.getBlockedReason());
    }
}
