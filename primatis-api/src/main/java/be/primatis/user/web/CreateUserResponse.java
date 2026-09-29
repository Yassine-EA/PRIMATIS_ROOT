package be.primatis.user.web;
/**
 * Réponse retournée après la création d'un utilisateur.
 */
public record CreateUserResponse(UserResponse user, String initialPassword) {
}
