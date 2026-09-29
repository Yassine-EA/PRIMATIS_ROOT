package be.primatis.reservation;
/**
 * Projection utilisée pour préparer l'annulation d'une réservation.
 */
public interface ReservationCancellationSnapshot {

    Long getId();

    ReservationStatus getReservationStatus();

    Long getAssignedCopyId();

    Long getUserId();
}
