package be.primatis.article;

import be.primatis.config.ArticleMediaProperties;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

/**
 * Extrait l'URL de la première image présente dans le contenu d'un article.
 */
@Component
public class ArticleThumbnailExtractor {

    /**
     * Recherche la première image présente dans le contenu d'un article.
     *
     * @param content contenu HTML de l'article
     * @return URL de la première image, ou null si aucune image valide n'est
     *         trouvée
     */
    public String extractFirstImageUrl(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }

        Element image = Jsoup.parse(content).selectFirst("img");
        if (image == null) {
            return null;
        }

        String src = image.attr("src");
        if (src.isBlank() || !isExpectedImageSrc(src)) {
            return null;
        }

        return src;
    }

    private boolean isExpectedImageSrc(String src) {
        return src.startsWith(ArticleMediaProperties.PUBLIC_PATH_PREFIX)
                || src.startsWith("http://")
                || src.startsWith("https://");
    }
}
