package be.primatis.setting;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Accès aux paramètres applicatifs.
 */
public interface ApplicationSettingRepository extends JpaRepository<ApplicationSetting, Long> {
    /**
     * Recherche un paramètre par sa clé.
     *
     * @param settingKey clé du paramètre
     * @return paramètre correspondant, s'il existe
     */
    Optional<ApplicationSetting> findBySettingKey(String settingKey);
    @EntityGraph(attributePaths = "updatedByUser")
    /**
     * Retourne les paramètres triés par clé.
     *
     * @return liste des paramètres
     */
    List<ApplicationSetting> findAllByOrderBySettingKeyAsc();
}
