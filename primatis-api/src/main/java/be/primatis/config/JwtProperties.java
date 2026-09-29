package be.primatis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

import java.time.Duration;

/**
 * Configuration externe de l'infrastructure JWT.
 */
@ConfigurationProperties(prefix = "primatis.security.jwt")
public record JwtProperties(
        String issuer,
        String audience,
        Duration accessTokenTtl,
        Resource privateKeyLocation,
        Resource publicKeyLocation) {
}
