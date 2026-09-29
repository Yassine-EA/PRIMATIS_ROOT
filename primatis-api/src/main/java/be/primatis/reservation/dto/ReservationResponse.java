package be.primatis.reservation.dto;

import be.primatis.catalogue.Copy;
import be.primatis.loan.Loan;
import be.primatis.reservation.Reservation;
import be.primatis.reservation.ReservationStatus;

import java.time.Instant;
import java.util.Objects;
/**
 * Représentation complète d'une réservation exposée par l'API.
 */
public record ReservationResponse(
        Long id,
        ReservationMemberResponse member,
        ReservationTitleResponse title,
        ReservationCopyResponse assignedCopy,
        Long fulfilledByLoanId,
        Instant reservationDate,
        Instant expirationDate,
        ReservationStatus reservationStatus,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Crée la représentation réservation.
     *
     * @param reservation réservation à représenter
     * @return représentation réservation
     */
    public static ReservationResponse from(Reservation reservation) {
        Objects.requireNonNull(reservation, "reservation");

        Copy assignedCopy = reservation.getAssignedCopy();
        Loan fulfilledByLoan = reservation.getFulfilledByLoan();

        return new ReservationResponse(
                reservation.getId(),
                ReservationMemberResponse.from(reservation.getUser()),
                ReservationTitleResponse.from(reservation.getTitle()),
                assignedCopy == null ? null : ReservationCopyResponse.from(assignedCopy),
                fulfilledByLoan == null ? null : fulfilledByLoan.getId(),
                reservation.getReservationDate(),
                reservation.getExpirationDate(),
                reservation.getReservationStatus(),
                reservation.getCreatedAt(),
                reservation.getUpdatedAt());
    }
}
