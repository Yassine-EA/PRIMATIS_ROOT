package be.primatis.security.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
/**
 * Requête d'authentification par adresse e-mail et mot de passe.
 */
public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password) {
}
