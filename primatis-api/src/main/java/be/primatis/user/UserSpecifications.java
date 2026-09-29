package be.primatis.user;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
/**
 * Construit les critères de recherche utilisés pour filtrer les utilisateurs.
 */
public final class UserSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private UserSpecifications() {
    }
    public static Specification<AppUser> matching(
            String q,
            AccountStatus accountStatus,
            MemberStatus memberStatus,
            LocalDate createdDateFrom,
            LocalDate createdDateTo,
            LocalDate expirationDateFrom,
            LocalDate expirationDateTo,
            LocalDate today) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (q != null && !q.isBlank()) {
                String pattern = "%" + escapeLike(q.trim().toLowerCase()) + "%";
                Expression<String> first = cb.lower(root.get("firstName"));
                Expression<String> last = cb.lower(root.get("lastName"));
                Expression<String> firstLast = cb.concat(cb.concat(first, " "), last);
                Expression<String> lastFirst = cb.concat(cb.concat(last, " "), first);
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("memberNumber")), pattern, LIKE_ESCAPE),
                        cb.like(first, pattern, LIKE_ESCAPE),
                        cb.like(last, pattern, LIKE_ESCAPE),
                        cb.like(firstLast, pattern, LIKE_ESCAPE),
                        cb.like(lastFirst, pattern, LIKE_ESCAPE),
                        cb.like(cb.lower(root.get("email")), pattern, LIKE_ESCAPE)));
            }

            if (accountStatus != null) {
                predicates.add(cb.equal(root.get("accountStatus"), accountStatus));
            }

            if (memberStatus != null) {
                Predicate storedStatus = cb.equal(root.get("memberStatus"), memberStatus);
                Predicate storedActive = cb.equal(root.get("memberStatus"), MemberStatus.ACTIVE);
                Predicate pastExpiration = cb.lessThan(root.<LocalDate>get("memberExpirationDate"), today);
                switch (memberStatus) {
                    case ACTIVE -> predicates.add(cb.and(storedStatus, cb.not(pastExpiration)));
                    case EXPIRED -> predicates.add(cb.or(storedStatus, cb.and(storedActive, pastExpiration)));
                    case BLOCKED -> predicates.add(storedStatus);
                }
            }

            addCreatedAtRange(predicates, cb, root.<Instant>get("createdAt"), createdDateFrom, createdDateTo);

            if (expirationDateFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<LocalDate>get("memberExpirationDate"), expirationDateFrom));
            }
            if (expirationDateTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.<LocalDate>get("memberExpirationDate"), expirationDateTo));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static void addCreatedAtRange(
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
