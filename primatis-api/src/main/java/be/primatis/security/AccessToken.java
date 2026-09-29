package be.primatis.security;

import java.time.Instant;
/**
 * Représente un jeton d'accès JWT généré après authentification.
 */
public record AccessToken(String token, String tokenType, Instant expiresAt, long expiresInSeconds) {
}
