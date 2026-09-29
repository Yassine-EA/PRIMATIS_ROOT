package be.primatis.setting;

import be.primatis.exception.BusinessRuleException;
import be.primatis.exception.ResourceNotFoundException;
import be.primatis.setting.web.SettingResponse;
import be.primatis.user.AppUserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
/**
 * Service centralisant la lecture et la gestion des paramètres applicatifs.
 */
@Service
public class ApplicationSettingService {

    private static final String SETTING_NOT_FOUND_CODE = "SETTING_NOT_FOUND";
    private static final String INTEGER_TYPE = "INTEGER";
    private static final String DECIMAL_TYPE = "DECIMAL";

    private final ApplicationSettingRepository applicationSettingRepository;
    private final AppUserRepository appUserRepository;
    private final Clock clock;

    public ApplicationSettingService(
            ApplicationSettingRepository applicationSettingRepository,
            AppUserRepository appUserRepository,
            Clock clock) {
        this.applicationSettingRepository = applicationSettingRepository;
        this.appUserRepository = appUserRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    /**
     * Retourne la valeur entière d'un paramètre.
     *
     * @param key clé du paramètre
     * @return valeur entière
     */
    public int getInteger(String key) {
        ApplicationSetting setting = applicationSettingRepository.findBySettingKey(key)
                .orElseThrow(() -> new IllegalStateException(
                        "Paramètre applicatif obligatoire absent : " + key));

        if (!"INTEGER".equals(setting.getValueType())) {
            throw new IllegalStateException("Paramètre applicatif " + key
                    + " n'est pas de type INTEGER (value_type=" + setting.getValueType() + ").");
        }

        try {
            return Integer.parseInt(setting.getSettingValue());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Paramètre applicatif " + key
                    + " contient une valeur INTEGER invalide : " + setting.getSettingValue(), e);
        }
    }

    @Transactional(readOnly = true)
    /**
     * Retourne la valeur décimale d'un paramètre.
     *
     * @param key clé du paramètre
     * @return valeur décimale
     */
    public BigDecimal getDecimal(String key) {
        ApplicationSetting setting = applicationSettingRepository.findBySettingKey(key)
                .orElseThrow(() -> new IllegalStateException(
                        "Paramètre applicatif obligatoire absent : " + key));

        if (!"DECIMAL".equals(setting.getValueType())) {
            throw new IllegalStateException("Paramètre applicatif " + key
                    + " n'est pas de type DECIMAL (value_type=" + setting.getValueType() + ").");
        }

        try {
            return new BigDecimal(setting.getSettingValue());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Paramètre applicatif " + key
                    + " contient une valeur DECIMAL invalide : " + setting.getSettingValue(), e);
        }
    }
    @PreAuthorize("hasAuthority('SETTING_READ')")
    @Transactional(readOnly = true)
    /**
     * Retourne l'ensemble des paramètres applicatifs.
     *
     * @return liste des paramètres
     */
    public List<SettingResponse> listSettings() {
        return applicationSettingRepository.findAllByOrderBySettingKeyAsc().stream()
                .map(SettingResponse::from)
                .toList();
    }
    @PreAuthorize("hasAuthority('SETTING_MANAGE')")
    @Transactional
    /**
     * Modifie la valeur d'un paramètre existant après validation de son type.
     *
     * @param settingKey clé du paramètre
     * @param rawValue nouvelle valeur
     * @param actingUserId identifiant de l'utilisateur effectuant la modification
     * @return paramètre mis à jour
     */
    public SettingResponse updateSettingValue(String settingKey, String rawValue, Long actingUserId) {
        ApplicationSetting setting = applicationSettingRepository.findBySettingKey(settingKey)
                .orElseThrow(() -> new ResourceNotFoundException(
                        SETTING_NOT_FOUND_CODE, "Aucun paramètre applicatif pour la clé " + settingKey + "."));

        String normalizedValue = rawValue.trim();
        validateValueForType(settingKey, setting.getValueType(), normalizedValue);

        setting.setSettingValue(normalizedValue);
        setting.setUpdatedAt(clock.instant());
        setting.setUpdatedByUser(appUserRepository.getReferenceById(actingUserId));

        return SettingResponse.from(setting);
    }

    private void validateValueForType(String settingKey, String valueType, String value) {
        switch (valueType) {
            case INTEGER_TYPE -> validatePositiveInteger(settingKey, value);
            case DECIMAL_TYPE -> validatePositiveDecimal(settingKey, value);
            default -> throw new BusinessRuleException("SETTING_VALUE_TYPE_NOT_SUPPORTED",
                    "Le paramètre " + settingKey + " est de type " + valueType
                            + ", non pris en charge par la modification administrative en V1.");
        }
    }

    private void validatePositiveInteger(String settingKey, String value) {
        int parsed;
        try {
            parsed = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new BusinessRuleException("SETTING_VALUE_NOT_INTEGER",
                    "La valeur de " + settingKey + " doit être un entier : " + value + ".");
        }
        if (parsed <= 0) {
            throw new BusinessRuleException("SETTING_VALUE_NOT_POSITIVE",
                    "La valeur de " + settingKey + " doit être strictement positive.");
        }
    }

    private void validatePositiveDecimal(String settingKey, String value) {
        BigDecimal parsed;
        try {
            parsed = new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new BusinessRuleException("SETTING_VALUE_NOT_DECIMAL",
                    "La valeur de " + settingKey + " doit être un nombre décimal : " + value + ".");
        }
        if (parsed.signum() <= 0) {
            throw new BusinessRuleException("SETTING_VALUE_NOT_POSITIVE",
                    "La valeur de " + settingKey + " doit être strictement positive.");
        }
    }
}
