package be.primatis.reservation;

import be.primatis.catalogue.Title;
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
 * Construit les critères de recherche utilisés pour filtrer les réservations.
 */
public final class ReservationSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private ReservationSpecifications() {
    }

    /**
     * Construit les critères de filtrage des réservations à partir des paramètres fournis.
     */
    public static Specification<Reservation> matching(
            String q,
            ReservationStatus reservationStatus,
            LocalDate reservationDateFrom,
            LocalDate reservationDateTo,
            LocalDate expirationDateFrom,
            LocalDate expirationDateTo) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (q != null && !q.isBlank()) {
                String pattern = "%" + escapeLike(q.trim().toLowerCase()) + "%";
                Join<Reservation, AppUser> user = root.join("user");
                Join<Reservation, Title> title = root.join("title");
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

            if (reservationStatus != null) {
                predicates.add(cb.equal(root.get("reservationStatus"), reservationStatus));
            }

            addInstantRange(predicates, cb, root.<Instant>get("reservationDate"), reservationDateFrom, reservationDateTo);
            addInstantRange(predicates, cb, root.<Instant>get("expirationDate"), expirationDateFrom, expirationDateTo);

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static void addInstantRange(
            List<Predicate> predicates,
            jakarta.persistence.criteria.CriteriaBuilder cb,
            Expression<Instant> path,
            LocalDate from,
            LocalDate to) {
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
