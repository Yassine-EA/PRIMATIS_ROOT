package be.primatis.reservation;

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

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Accès aux données des réservations.
 */
public interface ReservationRepository extends JpaRepository<Reservation, Long>, JpaSpecificationExecutor<Reservation> {
        /** Recherche les réservations actives d'un utilisateur pour un titre. */
        List<Reservation> findByUserIdAndTitleIdAndReservationStatusIn(
                        Long userId, Long titleId, Collection<ReservationStatus> reservationStatuses);

        /**
         * Compte les réservations d'un utilisateur correspondant aux statuts fournis.
         */
        long countByUserIdAndReservationStatusIn(Long userId, Collection<ReservationStatus> reservationStatuses);

        /** Retourne les réservations d'un utilisateur sous forme paginée. */
        @EntityGraph(attributePaths = { "user", "title", "assignedCopy" })
        Page<Reservation> findByUserId(Long userId, Pageable pageable);

        /** Retourne l'ensemble des réservations sous forme paginée. */
        @Override
        @EntityGraph(attributePaths = { "user", "title", "assignedCopy" })
        Page<Reservation> findAll(Pageable pageable);

        /** Retourne les réservations correspondant aux critères fournis. */
        @Override
        @EntityGraph(attributePaths = { "user", "title", "assignedCopy" })
        Page<Reservation> findAll(Specification<Reservation> spec, Pageable pageable);

        /** Recherche la réservation associée à un exemplaire pour un statut donné. */
        Optional<Reservation> findByAssignedCopyIdAndReservationStatus(
                        Long assignedCopyId, ReservationStatus reservationStatus);

        /**
         * Retourne les identifiants des réservations expirées dans un ordre
         * déterministe.
         */
        @Query("SELECT r.id FROM Reservation r WHERE r.reservationStatus = :status "
                        + "AND r.expirationDate <= :referenceInstant ORDER BY r.expirationDate ASC, r.id ASC")
        List<Long> findExpiredReservationIds(
                        @Param("status") ReservationStatus status,
                        @Param("referenceInstant") Instant referenceInstant,
                        Pageable pageable);

        /** Retourne les réservations en attente d'un titre selon l'ordre FIFO. */
        @Query("SELECT r.id FROM Reservation r WHERE r.title.id = :titleId AND r.reservationStatus = :status "
                        + "AND r.id NOT IN :excludedIds ORDER BY r.reservationDate ASC, r.id ASC")
        List<Long> findWaitingReservationIdsForTitleOrderedByFifo(
                        @Param("titleId") Long titleId,
                        @Param("status") ReservationStatus status,
                        @Param("excludedIds") Collection<Long> excludedIds,
                        Pageable pageable);

        /**
         * Recherche une réservation par son identifiant en appliquant un verrou
         * d'écriture.
         */
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("SELECT r FROM Reservation r WHERE r.id = :id")
        Optional<Reservation> findByIdForUpdate(@Param("id") Long id);

        /**
         * Retourne les informations nécessaires pour préparer l'annulation d'une
         * réservation.
         */
        @Query("SELECT r.id AS id, r.reservationStatus AS reservationStatus, "
                        + "r.assignedCopy.id AS assignedCopyId, r.user.id AS userId "
                        + "FROM Reservation r WHERE r.id = :id")
        Optional<ReservationCancellationSnapshot> findCancellationSnapshotById(@Param("id") Long id);
}
