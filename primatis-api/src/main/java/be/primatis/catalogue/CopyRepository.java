package be.primatis.catalogue;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Accès aux données des exemplaires du catalogue.
 */
public interface CopyRepository extends JpaRepository<Copy, Long> {

    /**
     * Recherche un exemplaire à partir de son code d'inventaire.
     *
     * @param inventoryCode code d'inventaire de l'exemplaire
     * @return exemplaire correspondant, s'il existe
     */
    Optional<Copy> findByInventoryCode(String inventoryCode);

    /**
     * Vérifie si un exemplaire existe déjà avec le code d'inventaire fourni.
     *
     * @param inventoryCode code d'inventaire
     * @return true si un exemplaire existe avec ce code
     */
    boolean existsByInventoryCode(String inventoryCode);

    /**
     * Recherche un exemplaire appartenant à un titre donné.
     *
     * @param id      identifiant de l'exemplaire
     * @param titleId identifiant du titre
     * @return exemplaire correspondant, s'il existe
     */
    Optional<Copy> findByIdAndTitleId(Long id, Long titleId);

    /**
     * Retourne les exemplaires associés à un titre, triés par code d'inventaire.
     *
     * @param titleId identifiant du titre
     * @return liste des exemplaires
     */
    List<Copy> findByTitleIdOrderByInventoryCodeAsc(Long titleId);

    /**
     * Vérifie si un titre possède au moins un exemplaire.
     *
     * @param titleId identifiant du titre
     * @return true si au moins un exemplaire est associé au titre
     */
    boolean existsByTitleId(Long titleId);

    /**
     * Vérifie si un titre possède au moins un exemplaire avec le statut demandé.
     *
     * @param titleId            identifiant du titre
     * @param availabilityStatus statut de disponibilité recherché
     * @return true si un exemplaire correspondant existe
     */
    boolean existsByTitleIdAndAvailabilityStatus(
            Long titleId,
            AvailabilityStatus availabilityStatus);

    /**
     * Projection du nombre d'exemplaires disponibles pour un titre.
     */
    interface TitleAvailableCopies {

        Long getTitleId();

        Long getAvailableCopies();
    }

    /**
     * Compte les exemplaires correspondant au statut demandé pour plusieurs titres.
     *
     * @param titleIds identifiants des titres
     * @param status   statut de disponibilité recherché
     * @return nombre d'exemplaires correspondants par titre
     */
    @Query("SELECT c.title.id AS titleId, COUNT(c) AS availableCopies FROM Copy c "
            + "WHERE c.title.id IN :titleIds AND c.availabilityStatus = :status "
            + "GROUP BY c.title.id")
    List<TitleAvailableCopies> countCopiesByTitleIdsAndAvailabilityStatus(
            @Param("titleIds") Collection<Long> titleIds,
            @Param("status") AvailabilityStatus status);

    /**
     * Recherche un exemplaire par son identifiant en appliquant un verrou
     * d'écriture.
     *
     * @param id identifiant de l'exemplaire
     * @return exemplaire correspondant, s'il existe
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Copy c WHERE c.id = :id")
    Optional<Copy> findByIdForUpdate(@Param("id") Long id);
}