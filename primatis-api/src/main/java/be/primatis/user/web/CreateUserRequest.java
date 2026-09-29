package be.primatis.user.web;

import be.primatis.user.MemberStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.time.LocalDate;
import java.util.Set;
/**
 * Requête de création d'un utilisateur.
 */
public record CreateUserRequest(
        @NotBlank @Email String email,
        @NotBlank String firstName,
        @NotBlank String lastName,
        String phoneNumber,
        @NotEmpty Set<String> roles,
        MemberStatus memberStatus,
        LocalDate registrationDate,
        LocalDate memberExpirationDate,
        String blockedReason) {
}
