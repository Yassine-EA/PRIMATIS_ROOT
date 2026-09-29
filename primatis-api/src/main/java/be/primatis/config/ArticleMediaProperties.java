package be.primatis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration du stockage des images utilisées dans le contenu des articles.
 */
@ConfigurationProperties(prefix = "primatis.article-media")
public record ArticleMediaProperties(String storagePath) {

    public ArticleMediaProperties {
        UnresolvedPlaceholderGuard.requireResolved("primatis.article-media.storage-path", storagePath);
    }

    /**
     * Préfixe de l'URL publique utilisée pour servir les images des articles.
     */
    public static final String PUBLIC_PATH_PREFIX = "/media/articles/";
}
