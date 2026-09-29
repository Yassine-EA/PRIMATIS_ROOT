package be.primatis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Configuration des origines autorisées par la politique CORS.
 */
@ConfigurationProperties(prefix = "primatis.security.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        if (allowedOrigins != null) {
            allowedOrigins.forEach(origin ->
                    UnresolvedPlaceholderGuard.requireResolved("primatis.security.cors.allowed-origins", origin));
        }
    }
}
