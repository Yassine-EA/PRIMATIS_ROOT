package be.primatis.fine.dto;

import be.primatis.fine.Fine;
import be.primatis.fine.FineStatus;
import be.primatis.loan.Loan;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;


/**
 * Représentation complète d'une amende exposée par l'API.
 */
public record FineResponse(
        Long id,
        FineBorrowerResponse borrower,
        FineLoanResponse loan,
        BigDecimal amount,
        String reason,
        Instant issuedAt,
        FineStatus fineStatus,
        Instant paidAt,
        Instant cancelledAt) {

    /**
     * Crée la représentation de l'amende.
     *
     * @param fine amende à représenter
     * @return représentation de l'amende
     */
    public static FineResponse from(Fine fine) {
        Objects.requireNonNull(fine, "fine");

        Loan loan = fine.getLoan();

        return new FineResponse(
                fine.getId(),
                FineBorrowerResponse.from(loan.getUser()),
                FineLoanResponse.from(loan),
                fine.getAmount(),
                fine.getReason(),
                fine.getIssuedAt(),
                fine.getFineStatus(),
                fine.getPaidAt(),
                fine.getCancelledAt());
    }
}
