package be.primatis.reservation;

/**
 * Statuts possibles d'une réservation.
 */
public enum ReservationStatus {
    WAITING,
    READY,
    FULFILLED,
    CANCELLED,
    EXPIRED
}
