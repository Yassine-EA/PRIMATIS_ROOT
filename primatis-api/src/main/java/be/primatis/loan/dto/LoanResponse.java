package be.primatis.loan.dto;

import be.primatis.loan.Loan;
import be.primatis.loan.LoanStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
/**
 * Représentation complète d'un prêt exposée par l'API.
 */
public record LoanResponse(
        Long id,
        LoanBorrowerResponse borrower,
        LoanCopyResponse copy,
        Instant loanDate,
        LocalDate dueDate,
        LocalDate returnDate,
        LoanStatus loanStatus,
        String notes,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Crée la représentation d'un prêt.
     *
     * @param loan prêt à représenter
     * @return représentation du prêt
     */
    public static LoanResponse from(Loan loan) {
        Objects.requireNonNull(loan, "loan");
        return new LoanResponse(
                loan.getId(),
                LoanBorrowerResponse.from(loan.getUser()),
                LoanCopyResponse.from(loan.getCopy()),
                loan.getLoanDate(),
                loan.getDueDate(),
                loan.getReturnDate(),
                loan.getLoanStatus(),
                loan.getNotes(),
                loan.getCreatedAt(),
                loan.getUpdatedAt());
    }
}
