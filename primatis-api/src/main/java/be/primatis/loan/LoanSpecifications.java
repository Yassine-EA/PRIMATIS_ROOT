package be.primatis.loan;

import be.primatis.user.AppUser;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
/**
 * Construit les critères de recherche utilisés pour filtrer les prêts.
 */
public final class LoanSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private LoanSpecifications() {
    }

    /**
     * Construit les critères de filtrage des prêts à partir des paramètres fournis.
     *
     * @param q texte recherché
     * @param loanStatus statut recherché
     * @param loanDateFrom date minimale du prêt
     * @param loanDateTo date maximale du prêt
     * @param dueDateFrom date minimale d'échéance
     * @param dueDateTo date maximale d'échéance
     * @return critères de recherche à appliquer
     */
    public static Specification<Loan> matching(
            String q,
            LoanStatus loanStatus,
            LocalDate loanDateFrom,
            LocalDate loanDateTo,
            LocalDate dueDateFrom,
            LocalDate dueDateTo) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (q != null && !q.isBlank()) {
                String pattern = "%" + escapeLike(q.trim().toLowerCase()) + "%";
                Join<Loan, AppUser> user = root.join("user");
                Expression<String> first = cb.lower(user.get("firstName"));
                Expression<String> last = cb.lower(user.get("lastName"));
                Expression<String> firstLast = cb.concat(cb.concat(first, " "), last);
                Expression<String> lastFirst = cb.concat(cb.concat(last, " "), first);
                predicates.add(cb.or(
                        cb.like(first, pattern, LIKE_ESCAPE),
                        cb.like(last, pattern, LIKE_ESCAPE),
                        cb.like(firstLast, pattern, LIKE_ESCAPE),
                        cb.like(lastFirst, pattern, LIKE_ESCAPE),
                        cb.like(cb.lower(user.get("memberNumber")), pattern, LIKE_ESCAPE)));
            }

            if (loanStatus != null) {
                predicates.add(cb.equal(root.get("loanStatus"), loanStatus));
            }

            if (loanDateFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                        root.<Instant>get("loanDate"), loanDateFrom.atStartOfDay(ZoneOffset.UTC).toInstant()));
            }
            if (loanDateTo != null) {
                predicates.add(cb.lessThan(
                        root.<Instant>get("loanDate"), loanDateTo.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()));
            }

            if (dueDateFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<LocalDate>get("dueDate"), dueDateFrom));
            }
            if (dueDateTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.<LocalDate>get("dueDate"), dueDateTo));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
    private static String escapeLike(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
