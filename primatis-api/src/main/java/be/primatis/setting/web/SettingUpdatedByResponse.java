package be.primatis.setting.web;

import be.primatis.user.AppUser;

import java.util.Objects;
/**
 * Représentation synthétique de l'utilisateur ayant modifié un paramètre.
 */
public record SettingUpdatedByResponse(Long id, String firstName, String lastName) {

    /**
     * Crée la représentation synthétique d'un utilisateur.
     *
     * @param appUser utilisateur à représenter
     * @return représentation de l'utilisateur
     */
    public static SettingUpdatedByResponse from(AppUser appUser) {
        Objects.requireNonNull(appUser, "appUser");
        return new SettingUpdatedByResponse(appUser.getId(), appUser.getFirstName(), appUser.getLastName());
    }
}
