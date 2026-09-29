package be.primatis.loan;

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

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Accès aux données des prêts.
 */
public interface LoanRepository extends JpaRepository<Loan, Long>, JpaSpecificationExecutor<Loan> {
    List<Loan> findByCopyIdAndLoanStatusIn(Long copyId, Collection<LoanStatus> loanStatuses);
    /**
     * Compte les prêts ouverts d'un utilisateur.
     *
     * @param userId identifiant de l'utilisateur
     * @param loanStatuses statuts considérés comme ouverts
     * @return nombre de prêts correspondants
     */
    long countByUserIdAndLoanStatusIn(Long userId, Collection<LoanStatus> loanStatuses);
    /**
     * Retourne les prêts d'un utilisateur sous forme paginée.
     *
     * @param userId identifiant de l'utilisateur
     * @param pageable paramètres de pagination
     * @return page des prêts de l'utilisateur
     */
    @EntityGraph(attributePaths = {"user", "copy", "copy.title"})
    Page<Loan> findByUserId(Long userId, Pageable pageable);
    /**
     * Retourne l'ensemble des prêts sous forme paginée avec les relations nécessaires à leur représentation.
     *
     * @param pageable paramètres de pagination
     * @return page des prêts
     */
    @Override
    @EntityGraph(attributePaths = {"user", "copy", "copy.title"})
    Page<Loan> findAll(Pageable pageable);
    /**
     * Retourne les prêts correspondant aux critères fournis.
     *
     * @param spec critères de recherche
     * @param pageable paramètres de pagination
     * @return page des prêts correspondants
     */
    @Override
    @EntityGraph(attributePaths = {"user", "copy", "copy.title"})
    Page<Loan> findAll(Specification<Loan> spec, Pageable pageable);
    /**
     * Recherche un prêt par son identifiant en appliquant un verrou d'écriture.
     *
     * @param id identifiant du prêt
     * @return prêt correspondant, s'il existe
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM Loan l WHERE l.id = :id")
    Optional<Loan> findByIdForUpdate(@Param("id") Long id);
    @Query("SELECT l.id FROM Loan l WHERE l.loanStatus = :status AND l.dueDate < :referenceDate "
            + "ORDER BY l.dueDate ASC, l.id ASC")
    List<Long> findOverdueLoanIds(
            @Param("status") LoanStatus status,
            @Param("referenceDate") LocalDate referenceDate,
            Pageable pageable);
    @Query("SELECT l.id FROM Loan l WHERE l.loanStatus = :status AND l.dueDate > :referenceDate "
            + "AND l.dueDate <= :upperBound ORDER BY l.dueDate ASC, l.id ASC")
    /**
     * Retourne les identifiants des prêts actifs arrivant prochainement à échéance.
     *
     * @param referenceDate date de référence
     * @param upperBound borne supérieure incluse
     * @return identifiants des prêts concernés
     */
    List<Long> findDueSoonLoanIds(
            @Param("status") LoanStatus status,
            @Param("referenceDate") LocalDate referenceDate,
            @Param("upperBound") LocalDate upperBound,
            Pageable pageable);
}
