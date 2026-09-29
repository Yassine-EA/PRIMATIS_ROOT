package be.primatis.article;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Génère et normalise les slugs utilisés pour identifier les articles.
 */
@Component
public class ArticleSlugGenerator {

    /**
     * Longueur maximale du slug de base avant ajout d'un éventuel suffixe.
     */
    static final int MAX_BASE_SLUG_LENGTH = 240;

    /**
     * Nombre maximal de tentatives pour trouver un slug disponible.
     */
    static final int MAX_SUFFIX_ATTEMPTS = 1000;

    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALPHANUMERIC_RUN = Pattern.compile("[^a-z0-9]+");
    private static final Pattern LEADING_OR_TRAILING_HYPHENS = Pattern.compile("^-+|-+$");

    /**
     * Normalise un titre afin de produire un slug.
     *
     * @param title titre à normaliser
     * @return slug normalisé
     * @throws NullPointerException     si le titre est null
     * @throws IllegalArgumentException si le titre ne permet pas de produire un
     *                                  slug
     */
    public String normalize(String title) {
        Objects.requireNonNull(title, "title");

        String lowerCased = title.trim().toLowerCase(java.util.Locale.ROOT);
        String decomposed = Normalizer.normalize(lowerCased, Normalizer.Form.NFD);
        String withoutDiacritics = COMBINING_MARKS.matcher(decomposed).replaceAll("");
        String hyphenated = NON_ALPHANUMERIC_RUN.matcher(withoutDiacritics).replaceAll("-");
        String trimmedHyphens = LEADING_OR_TRAILING_HYPHENS.matcher(hyphenated).replaceAll("");

        String truncated = trimmedHyphens.length() > MAX_BASE_SLUG_LENGTH
                ? trimmedHyphens.substring(0, MAX_BASE_SLUG_LENGTH)
                : trimmedHyphens;
        String result = LEADING_OR_TRAILING_HYPHENS.matcher(truncated).replaceAll("");

        if (result.isEmpty()) {
            throw new IllegalArgumentException(
                    "La normalisation du titre '%s' produit un slug vide.".formatted(title));
        }
        return result;
    }

    /**
     * Génère un slug disponible à partir d'un titre.
     *
     * Un suffixe numérique est ajouté lorsque le slug initial existe déjà.
     *
     * @param title      titre utilisé pour générer le slug
     * @param slugExists fonction permettant de vérifier si un slug existe déjà
     * @return slug disponible
     * @throws IllegalStateException si aucun slug disponible n'est trouvé
     */
    public String generateUniqueSlug(String title, Predicate<String> slugExists) {
        Objects.requireNonNull(slugExists, "slugExists");
        String base = normalize(title);

        if (!slugExists.test(base)) {
            return base;
        }
        for (int suffix = 2; suffix <= MAX_SUFFIX_ATTEMPTS; suffix++) {
            String candidate = base + "-" + suffix;
            if (!slugExists.test(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "Impossible de résoudre un slug unique pour '%s' après %d tentatives."
                        .formatted(title, MAX_SUFFIX_ATTEMPTS));
    }
}
