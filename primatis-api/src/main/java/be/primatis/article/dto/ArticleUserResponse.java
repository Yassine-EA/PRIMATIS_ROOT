package be.primatis.article.dto;

import be.primatis.user.AppUser;

import java.util.Objects;

/**
 * Représentation synthétique d'un utilisateur associé à un article.
 */
public record ArticleUserResponse(Long id, String firstName, String lastName) {

    /**
     * Crée la représentation synthétique d'un utilisateur.
     *
     * @param appUser utilisateur à représenter
     * @return représentation synthétique de l'utilisateur
     */
    public static ArticleUserResponse from(AppUser appUser) {
        Objects.requireNonNull(appUser, "appUser");
        return new ArticleUserResponse(appUser.getId(), appUser.getFirstName(), appUser.getLastName());
    }
}
