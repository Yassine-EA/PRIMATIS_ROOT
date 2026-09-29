package be.primatis.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

/**
 * Configure l'accès HTTP aux images stockées pour les articles.
 */
@Configuration
@EnableConfigurationProperties(ArticleMediaProperties.class)
public class ArticleMediaWebConfig implements WebMvcConfigurer {

    private final ArticleMediaProperties properties;

    public ArticleMediaWebConfig(ArticleMediaProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = "file:" + Path.of(properties.storagePath()).toAbsolutePath().normalize() + "/";
        registry.addResourceHandler(ArticleMediaProperties.PUBLIC_PATH_PREFIX + "**")
                .addResourceLocations(location);
    }
}
