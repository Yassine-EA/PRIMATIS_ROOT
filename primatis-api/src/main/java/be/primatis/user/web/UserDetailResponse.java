package be.primatis.user.web;

import java.util.List;
/**
 * Représentation détaillée d'un utilisateur exposée par l'API.
 */
public record UserDetailResponse(UserResponse user, List<String> roles) {
}
