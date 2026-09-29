package be.primatis.user.web;
/**
 * Requête de modification du profil personnel.
 */
public record UpdateMeProfileRequest(@ValidPhoneNumber String phoneNumber) {
}
