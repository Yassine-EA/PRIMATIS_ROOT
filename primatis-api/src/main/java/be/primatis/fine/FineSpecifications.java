package be.primatis.fine;

import be.primatis.catalogue.Copy;
import be.primatis.catalogue.Title;
import be.primatis.loan.Loan;
import be.primatis.user.AppUser;
import jakarta.persistence.criteria.CriteriaBuilder;
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
 * Construit les critères de recherche utilisés pour filtrer les amendes.
 */
public final class FineSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private FineSpecifications() {
    }

    /**
     * Construit les critères de filtrage des amendes à partir des paramètres fournis.
     *
     * @param q texte recherché
     * @param fineStatus statut recherché
     * @param issuedDateFrom date minimale d'émission
     * @param issuedDateTo date maximale d'émission
     * @param closedDateFrom date minimale de clôture
     * @param closedDateTo date maximale de clôture
     * @return critères de recherche à appliquer
     */
    public static Specification<Fine> matching(
            String q,
            FineStatus fineStatus,
            LocalDate issuedDateFrom,
            LocalDate issuedDateTo,
            LocalDate closedDateFrom,
            LocalDate closedDateTo) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (q != null && !q.isBlank()) {
                String pattern = "%" + escapeLike(q.trim().toLowerCase()) + "%";
                Join<Fine, Loan> loan = root.join("loan");
                Join<Loan, AppUser> user = loan.join("user");
                Join<Loan, Copy> copy = loan.join("copy");
                Join<Copy, Title> title = copy.join("title");
                Expression<String> first = cb.lower(user.get("firstName"));
                Expression<String> last = cb.lower(user.get("lastName"));
                Expression<String> firstLast = cb.concat(cb.concat(first, " "), last);
                Expression<String> lastFirst = cb.concat(cb.concat(last, " "), first);
                predicates.add(cb.or(
                        cb.like(first, pattern, LIKE_ESCAPE),
                        cb.like(last, pattern, LIKE_ESCAPE),
                        cb.like(firstLast, pattern, LIKE_ESCAPE),
                        cb.like(lastFirst, pattern, LIKE_ESCAPE),
                        cb.like(cb.lower(user.get("memberNumber")), pattern, LIKE_ESCAPE),
                        cb.like(cb.lower(title.get("title")), pattern, LIKE_ESCAPE)));
            }

            if (fineStatus != null) {
                predicates.add(cb.equal(root.get("fineStatus"), fineStatus));
            }

            addInstantRange(predicates, cb, root.<Instant>get("issuedAt"), issuedDateFrom, issuedDateTo);
            if (closedDateFrom != null || closedDateTo != null) {
                Expression<Instant> closedAt = cb.coalesce(root.<Instant>get("paidAt"), root.<Instant>get("cancelledAt"));
                addInstantRange(predicates, cb, closedAt, closedDateFrom, closedDateTo);
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static void addInstantRange(
            List<Predicate> predicates, CriteriaBuilder cb, Expression<Instant> path, LocalDate from, LocalDate to) {
        if (from != null) {
            predicates.add(cb.greaterThanOrEqualTo(path, from.atStartOfDay(ZoneOffset.UTC).toInstant()));
        }
        if (to != null) {
            predicates.add(cb.lessThan(path, to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()));
        }
    }

    
    private static String escapeLike(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
