package be.primatis.setting.web;

import be.primatis.setting.ApplicationSetting;
import be.primatis.user.AppUser;

import java.time.Instant;
import java.util.Objects;
/**
 * Représentation d'un paramètre applicatif exposée par l'API.
 */
public record SettingResponse(
        String settingKey,
        String settingValue,
        String valueType,
        String description,
        Instant updatedAt,
        SettingUpdatedByResponse updatedByUser) {

    /**
     * Crée la représentation d'un paramètre applicatif.
     *
     * @param setting paramètre à représenter
     * @return représentation du paramètre
     */
    public static SettingResponse from(ApplicationSetting setting) {
        Objects.requireNonNull(setting, "setting");
        AppUser updatedByUser = setting.getUpdatedByUser();
        return new SettingResponse(
                setting.getSettingKey(),
                setting.getSettingValue(),
                setting.getValueType(),
                setting.getDescription(),
                setting.getUpdatedAt(),
                updatedByUser == null ? null : SettingUpdatedByResponse.from(updatedByUser));
    }
}
