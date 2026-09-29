package be.primatis.catalogue;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Construit les critères de recherche appliqués aux titres du catalogue.
 */
public final class TitleSpecifications {

    private TitleSpecifications() {
    }

    /**
     * Construit les critères de filtrage des titres à partir des paramètres
     * fournis.
     *
     * @param titleKeyword texte recherché dans le titre ou le nom d'un auteur
     * @param authorId     identifiant de l'auteur
     * @param genreCode    code du genre
     * @param language     langue du titre
     * @param titleStatus  statut du titre
     * @return critères de recherche à appliquer
     */
    public static Specification<Title> matching(
            String titleKeyword,
            Long authorId,
            String genreCode,
            Language language,
            TitleStatus titleStatus) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (titleKeyword != null && !titleKeyword.isBlank()) {
                Expression<String> pattern = accentInsensitive(
                        cb,
                        cb.literal("%" + titleKeyword.trim() + "%"));

                Subquery<Long> authorNameMatch = query.subquery(Long.class);
                Root<TitleAuthor> titleAuthorForKeyword = authorNameMatch.from(TitleAuthor.class);

                authorNameMatch
                        .select(titleAuthorForKeyword.get("title").get("id"))
                        .where(cb.like(
                                accentInsensitive(
                                        cb,
                                        titleAuthorForKeyword
                                                .get("author")
                                                .get("fullName")),
                                pattern));

                predicates.add(cb.or(
                        cb.like(
                                accentInsensitive(cb, root.get("title")),
                                pattern),
                        root.get("id").in(authorNameMatch)));
            }

            if (authorId != null) {
                Subquery<Long> authorMatch = query.subquery(Long.class);
                Root<TitleAuthor> titleAuthor = authorMatch.from(TitleAuthor.class);

                authorMatch
                        .select(titleAuthor.get("title").get("id"))
                        .where(cb.equal(
                                titleAuthor.get("author").get("id"),
                                authorId));

                predicates.add(
                        root.get("id").in(authorMatch));
            }

            if (genreCode != null && !genreCode.isBlank()) {
                Subquery<Long> genreMatch = query.subquery(Long.class);
                Root<TitleGenre> titleGenre = genreMatch.from(TitleGenre.class);

                genreMatch
                        .select(titleGenre.get("title").get("id"))
                        .where(cb.equal(
                                titleGenre.get("genre").get("code"),
                                genreCode));

                predicates.add(
                        root.get("id").in(genreMatch));
            }

            if (language != null) {
                predicates.add(
                        cb.equal(root.get("language"), language));
            }

            if (titleStatus != null) {
                predicates.add(
                        cb.equal(root.get("titleStatus"), titleStatus));
            }

            return cb.and(
                    predicates.toArray(new Predicate[0]));
        };
    }

    private static Expression<String> accentInsensitive(
            CriteriaBuilder cb,
            Expression<String> expression) {

        return cb.function(
                "unaccent",
                String.class,
                cb.lower(expression));
    }
}