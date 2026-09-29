package be.primatis.reservation;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
/**
 * Déclenche périodiquement le traitement des réservations prêtes arrivées à expiration.
 */
@Component
public class ReservationExpirationScheduler {
    private static final String FIXED_DELAY_PROPERTY =
            "${primatis.reservation.expiration.fixed-delay-ms:60000}";
    private static final String INITIAL_DELAY_PROPERTY =
            "${primatis.reservation.expiration.initial-delay-ms:60000}";

    private final ReservationExpirationService reservationExpirationService;
    private final Clock clock;

    public ReservationExpirationScheduler(
            ReservationExpirationService reservationExpirationService, Clock clock) {
        this.reservationExpirationService = reservationExpirationService;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = FIXED_DELAY_PROPERTY, initialDelayString = INITIAL_DELAY_PROPERTY)
    /**
     * Lance le traitement des réservations prêtes expirées.
     */
    public void expireReadyReservations() {
        reservationExpirationService.processExpiredReadyReservations(clock.instant());
    }
}
