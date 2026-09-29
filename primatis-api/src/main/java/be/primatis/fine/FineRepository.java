package be.primatis.fine;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Accès aux données des amendes.
 */
public interface FineRepository extends JpaRepository<Fine, Long>, JpaSpecificationExecutor<Fine> {

    
    /**
     * Recherche l'amende associée à un prêt.
     *
     * @param loanId identifiant du prêt
     * @return amende correspondante, si elle existe
     */
    Optional<Fine> findByLoanId(Long loanId);

    
    /**
     * Vérifie si un utilisateur possède au moins une amende avec le statut demandé.
     *
     * @param userId identifiant de l'utilisateur
     * @param fineStatus statut recherché
     * @return true si une amende correspondante existe
     */
    boolean existsByLoanUserIdAndFineStatus(Long userId, FineStatus fineStatus);

    
    /**
     * Retourne les amendes d'un utilisateur sous forme paginée.
     *
     * @param userId identifiant de l'utilisateur
     * @param pageable paramètres de pagination
     * @return page des amendes de l'utilisateur
     */
    @EntityGraph(attributePaths = {"loan", "loan.user", "loan.copy", "loan.copy.title"})
    Page<Fine> findByLoanUserId(Long userId, Pageable pageable);

    
    /**
     * Retourne l'ensemble des amendes sous forme paginée avec les relations utiles à leur représentation.
     *
     * @param pageable paramètres de pagination
     * @return page des amendes
     */
    @Override
    @EntityGraph(attributePaths = {"loan", "loan.user", "loan.copy", "loan.copy.title"})
    Page<Fine> findAll(Pageable pageable);

    
    /**
     * Retourne les amendes correspondant aux critères fournis.
     *
     * @param spec critères de recherche
     * @param pageable paramètres de pagination
     * @return page des amendes correspondantes
     */
    @Override
    @EntityGraph(attributePaths = {"loan", "loan.user", "loan.copy", "loan.copy.title"})
    Page<Fine> findAll(Specification<Fine> spec, Pageable pageable);

    
    /**
     * Recherche une amende par son identifiant en appliquant un verrou d'écriture.
     *
     * @param id identifiant de l'amende
     * @return amende correspondante, si elle existe
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM Fine f WHERE f.id = :id")
    Optional<Fine> findByIdForUpdate(@Param("id") Long id);
}
