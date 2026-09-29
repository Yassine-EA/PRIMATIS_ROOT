package be.primatis.setting.web;

import jakarta.validation.constraints.NotBlank;
/**
 * Requête de modification de la valeur d'un paramètre applicatif.
 */
public record UpdateSettingValueRequest(@NotBlank String settingValue) {
}
