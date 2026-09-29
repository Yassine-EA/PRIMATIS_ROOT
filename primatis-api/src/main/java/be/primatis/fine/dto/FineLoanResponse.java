package be.primatis.fine.dto;

import be.primatis.loan.Loan;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;


/**
 * Représentation synthétique du prêt associé à une amende.
 */
public record FineLoanResponse(Long id, Instant loanDate, LocalDate dueDate, LocalDate returnDate, FineCopyResponse copy) {

    /**
     * Crée la représentation du prêt.
     *
     * @param loan prêt à représenter
     * @return représentation du prêt
     */
    public static FineLoanResponse from(Loan loan) {
        Objects.requireNonNull(loan, "loan");
        return new FineLoanResponse(
                loan.getId(),
                loan.getLoanDate(),
                loan.getDueDate(),
                loan.getReturnDate(),
                FineCopyResponse.from(loan.getCopy()));
    }
}
