package be.primatis.reservation;

import be.primatis.catalogue.AvailabilityStatus;
import be.primatis.catalogue.Copy;
import be.primatis.catalogue.CopyCondition;
import be.primatis.exception.ConflictException;
import be.primatis.notification.NotificationService;
import be.primatis.notification.NotificationType;
import be.primatis.setting.ApplicationSettingService;
import be.primatis.user.AppUser;
import be.primatis.user.MemberExpirationPolicy;
import be.primatis.user.MemberStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Service chargé d'affecter un exemplaire à la prochaine réservation
 * admissible.
 */
@Component
public class ReservationAssignmentService {

    private static final String RESERVATION_ASSIGNMENT_CONTENTION_CODE = "RESERVATION_ASSIGNMENT_CONTENTION";
    private static final String RESERVATION_READY_HOLD_HOURS_KEY = "RESERVATION_READY_HOLD_HOURS";
    private static final int MAX_ASSIGNMENT_ATTEMPTS = 20;
    private static final long NO_EXCLUSION_SENTINEL_ID = 0L;

    private final ReservationRepository reservationRepository;
    private final ApplicationSettingService applicationSettingService;
    private final MemberExpirationPolicy memberExpirationPolicy;
    private final NotificationService notificationService;

    public ReservationAssignmentService(
            ReservationRepository reservationRepository,
            ApplicationSettingService applicationSettingService,
            MemberExpirationPolicy memberExpirationPolicy,
            NotificationService notificationService) {
        this.reservationRepository = reservationRepository;
        this.applicationSettingService = applicationSettingService;
        this.memberExpirationPolicy = memberExpirationPolicy;
        this.notificationService = notificationService;
    }

    /**
     * Affecte l'exemplaire à la prochaine réservation admissible selon l'ordre
     * FIFO.
     * Si aucune réservation ne peut être servie, l'exemplaire redevient disponible.
     */
    public void assignNextAdmissibleWaitingReservationOrMakeAvailable(Copy copy, Instant now) {
        Set<Long> excludedIds = new LinkedHashSet<>();
        excludedIds.add(NO_EXCLUSION_SENTINEL_ID);

        for (int attempt = 0; attempt < MAX_ASSIGNMENT_ATTEMPTS; attempt++) {
            List<Long> candidateIds = reservationRepository.findWaitingReservationIdsForTitleOrderedByFifo(
                    copy.getTitle().getId(), ReservationStatus.WAITING, excludedIds, PageRequest.of(0, 1));

            if (candidateIds.isEmpty()) {
                releaseCopy(copy);
                return;
            }

            Long candidateId = candidateIds.get(0);
            Reservation reservation = reservationRepository.findByIdForUpdate(candidateId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Incohérence de données : Reservation " + candidateId
                                    + " disparue entre son identification et son verrouillage."));

            if (reservation.getReservationStatus() != ReservationStatus.WAITING) {
                // Raciné par un autre workflow concurrent (retour, annulation) entre
                // l'identification (non verrouillée) et ce verrouillage : son statut ne vaut
                // plus WAITING, donc le filtre de la requête l'exclura naturellement de la
                // prochaine itération — aucune exclusion explicite nécessaire.
                continue;
            }

            if (!isMemberAdmissible(reservation.getUser())) {
                // Non admissible mais toujours WAITING : jamais modifié, jamais réessayé
                excludedIds.add(candidateId);
                continue;
            }

            int holdHours = applicationSettingService.getInteger(RESERVATION_READY_HOLD_HOURS_KEY);
            reservation.setReservationStatus(ReservationStatus.READY);
            reservation.setAssignedCopy(copy);
            reservation.setExpirationDate(now.plus(holdHours, ChronoUnit.HOURS));
            reservation.setUpdatedAt(now);

            notificationService.createForReservation(reservation, NotificationType.RESERVATION_READY,
                    "Réservation disponible", "Votre réservation est prête à être retirée.");

            copy.setAvailabilityStatus(AvailabilityStatus.RESERVED);
            return;
        }

        throw new ConflictException(RESERVATION_ASSIGNMENT_CONTENTION_CODE,
                "Impossible d'assigner une Reservation WAITING après " + MAX_ASSIGNMENT_ATTEMPTS
                        + " tentatives : contention concurrente trop forte sur ce Title.");
    }

    private boolean isMemberAdmissible(AppUser member) {
        memberExpirationPolicy.syncIfNeeded(member);
        return member.getMemberStatus() == MemberStatus.ACTIVE;
    }

    private void releaseCopy(Copy copy) {
        if (copy.getCopyCondition() == CopyCondition.LOST || copy.getCopyCondition() == CopyCondition.OUT_OF_SERVICE) {
            copy.setAvailabilityStatus(AvailabilityStatus.UNAVAILABLE);
        } else {
            copy.setAvailabilityStatus(AvailabilityStatus.AVAILABLE);
        }
    }
}
