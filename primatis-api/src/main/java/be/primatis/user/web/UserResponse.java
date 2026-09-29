package be.primatis.user.web;

import be.primatis.user.AccountStatus;
import be.primatis.user.AppUser;
import be.primatis.user.MemberStatus;

import java.time.Instant;
import java.time.LocalDate;
/**
 * Représentation d'un utilisateur exposée par l'API.
 */
public record UserResponse(
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
        String blockedReason,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Crée la représentation correspondante.
     *
     * @param appUser objet à représenter
     * @return représentation créée
     */
    public static UserResponse from(AppUser appUser) {
        return new UserResponse(
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
                appUser.getBlockedReason(),
                appUser.getCreatedAt(),
                appUser.getUpdatedAt());
    }
}
