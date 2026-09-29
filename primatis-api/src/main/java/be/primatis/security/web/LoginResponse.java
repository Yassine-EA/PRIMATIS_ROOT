package be.primatis.security.web;

import be.primatis.security.AccessToken;

import java.time.Instant;
/**
 * Réponse retournée après une authentification réussie.
 */
public record LoginResponse(
        String token,
        String tokenType,
        Instant expiresAt,
        long expiresInSeconds) {

    public static LoginResponse from(AccessToken accessToken) {
        return new LoginResponse(
                accessToken.token(),
                accessToken.tokenType(),
                accessToken.expiresAt(),
                accessToken.expiresInSeconds());
    }
}
