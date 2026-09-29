package be.primatis.reservation;

import be.primatis.catalogue.Copy;
import be.primatis.catalogue.CopyRepository;
import be.primatis.notification.NotificationService;
import be.primatis.notification.NotificationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
/**
 * Service chargé d'expirer les réservations prêtes dont le délai de retrait est dépassé.
 */
@Service
public class ReservationExpirationService {

    private static final Logger log = LoggerFactory.getLogger(ReservationExpirationService.class);
    static final int EXPIRATION_BATCH_SIZE = 100;

    private final ReservationRepository reservationRepository;
    private final CopyRepository copyRepository;
    private final ReservationAssignmentService reservationAssignmentService;
    private final NotificationService notificationService;
    private final ReservationExpirationService self;

    public ReservationExpirationService(
            ReservationRepository reservationRepository,
            CopyRepository copyRepository,
            ReservationAssignmentService reservationAssignmentService,
            NotificationService notificationService,
            @Lazy ReservationExpirationService self) {
        this.reservationRepository = reservationRepository;
        this.copyRepository = copyRepository;
        this.reservationAssignmentService = reservationAssignmentService;
        this.notificationService = notificationService;
        this.self = self;
    }
    /**
     * Traite les réservations prêtes expirées à l'instant de référence.
     */
    public void processExpiredReadyReservations(Instant referenceInstant) {
        long startedAt = System.nanoTime();
        List<Long> candidateIds = reservationRepository.findExpiredReservationIds(
                ReservationStatus.READY, referenceInstant, PageRequest.of(0, EXPIRATION_BATCH_SIZE));

        int expired = 0;
        int failed = 0;
        for (Long candidateId : candidateIds) {
            try {
                if (self.expireReservationIfStillDue(candidateId, referenceInstant)) {
                    expired++;
                }
            } catch (RuntimeException ex) {
                failed++;
                log.error("Échec du traitement d'expiration pour la Reservation {} : {}",
                        candidateId, ex.getMessage(), ex);
            }
        }

        // Reservation. WARN en cas d'échec, INFO seulement si au moins une
        // Reservation a réellement expiré ; un cycle sans travail (toutes les
        // minutes) reste en DEBUG.
        long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
        if (failed > 0) {
            log.warn("Scheduler expiration des réservations : {} candidat(s), {} expirée(s), {} échec(s) en {} ms",
                    candidateIds.size(), expired, failed, durationMs);
        } else if (expired > 0) {
            log.info("Scheduler expiration des réservations : {} candidat(s), {} expirée(s) en {} ms",
                    candidateIds.size(), expired, durationMs);
        } else {
            log.debug("Scheduler expiration des réservations : {} candidat(s), 0 expirée(s) en {} ms",
                    candidateIds.size(), durationMs);
        }
    }
    @Transactional
    /**
     * Expire une réservation si elle est toujours prête et arrivée à échéance.
     */
    public boolean expireReservationIfStillDue(Long reservationId, Instant referenceInstant) {
        ReservationCancellationSnapshot snapshot =
                reservationRepository.findCancellationSnapshotById(reservationId).orElse(null);
        if (snapshot == null
                || snapshot.getReservationStatus() != ReservationStatus.READY
                || snapshot.getAssignedCopyId() == null) {
            return false;
        }

        Copy copy = copyRepository.findByIdForUpdate(snapshot.getAssignedCopyId())
                .orElseThrow(() -> new IllegalStateException(
                        "Incohérence de données : aucun exemplaire pour l'identifiant "
                                + snapshot.getAssignedCopyId() + ", pourtant référencé par le snapshot "
                                + "de la Reservation " + reservationId + "."));
        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new IllegalStateException(
                        "Incohérence de données : Reservation " + reservationId
                                + " disparue entre son instantané et son verrouillage."));

        if (reservation.getReservationStatus() != ReservationStatus.READY) {
            return false;
        }
        if (reservation.getAssignedCopy() == null || !reservation.getAssignedCopy().getId().equals(copy.getId())) {
            throw new IllegalStateException("Incohérence de données : Reservation " + reservationId
                    + " READY sans correspondance avec le Copy verrouillé " + copy.getId() + ".");
        }
        if (reservation.getExpirationDate() == null || reservation.getExpirationDate().isAfter(referenceInstant)) {
            return false;
        }

        reservation.setReservationStatus(ReservationStatus.EXPIRED);
        reservation.setUpdatedAt(referenceInstant);
        notificationService.createForReservation(reservation, NotificationType.RESERVATION_EXPIRED,
                "Réservation expirée", "Le délai de retrait de votre réservation est expiré.");
        reservationAssignmentService.assignNextAdmissibleWaitingReservationOrMakeAvailable(copy, referenceInstant);
        copy.setUpdatedAt(referenceInstant);
        return true;
    }
}
