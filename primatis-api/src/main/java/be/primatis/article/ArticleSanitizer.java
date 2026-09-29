package be.primatis.article;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Nettoie le contenu HTML d'un article avant sa persistance.
 *
 * Seuls les éléments et attributs autorisés sont conservés.
 */
@Component
public class ArticleSanitizer {

    /**
     * Liste des éléments HTML autorisés dans le contenu d'un article.
     */
    private static final Safelist ARTICLE_CONTENT_ALLOWLIST = Safelist.basic()
            .addTags("h2", "h3", "h4", "img")
            .addAttributes("img", "src", "alt")
            .removeProtocols("a", "href", "ftp", "mailto")
            .addProtocols("a", "href", "http", "https")
            .addProtocols("img", "src", "http", "https")
            .preserveRelativeLinks(true);

    /**
     * Nettoie un contenu HTML selon les règles autorisées pour les articles.
     *
     * @param rawHtml contenu HTML brut
     * @return contenu HTML nettoyé
     */
    public String sanitize(String rawHtml) {
        Objects.requireNonNull(rawHtml, "rawHtml");
        return Jsoup.clean(rawHtml, ARTICLE_CONTENT_ALLOWLIST);
    }
}
