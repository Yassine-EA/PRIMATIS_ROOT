package be.primatis.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.util.Base64;
import java.util.Locale;

/**
 * Configure l'infrastructure JWT RS256 et le chargement des clés RSA.
 */
@Configuration
@Profile("!test")
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

    @Bean
    public RSAPrivateKey rsaPrivateKey(JwtProperties properties) {
        byte[] decoded = decodePem(properties.privateKeyLocation());
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return (RSAPrivateKey) keyFactory.generatePrivate(new PKCS8EncodedKeySpec(decoded));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
            throw new IllegalStateException("Impossible de charger la clé privée RSA JWT.", ex);
        }
    }

    @Bean
    public RSAPublicKey rsaPublicKey(JwtProperties properties) {
        byte[] decoded = decodePem(properties.publicKeyLocation());
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return (RSAPublicKey) keyFactory.generatePublic(new X509EncodedKeySpec(decoded));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
            throw new IllegalStateException("Impossible de charger la clé publique RSA JWT.", ex);
        }
    }

    @Bean
    public JwtEncoder jwtEncoder(RSAPublicKey rsaPublicKey, RSAPrivateKey rsaPrivateKey) {
        return JwtCryptoSupport.buildEncoder(rsaPublicKey, rsaPrivateKey);
    }

    @Bean
    public JwtDecoder jwtDecoder(RSAPublicKey rsaPublicKey, JwtProperties properties, Clock clock) {
        return JwtCryptoSupport.buildDecoder(rsaPublicKey, properties.issuer(), properties.audience(), clock);
    }

    /**
     * Extrait et décode le contenu d'une clé RSA au format PEM.
     *
     * @param resource fichier PEM à lire
     * @return contenu binaire de la clé
     */
    private byte[] decodePem(Resource resource) {
        String content;
        try (var inputStream = resource.getInputStream()) {
            content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException("Impossible de lire le fichier de clé RSA JWT : " + resource, ex);
        }

        StringBuilder base64 = new StringBuilder();
        boolean insidePemBlock = false;
        for (String line : content.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.toUpperCase(Locale.ROOT).startsWith("-----BEGIN")) {
                insidePemBlock = true;
                continue;
            }
            if (trimmed.toUpperCase(Locale.ROOT).startsWith("-----END")) {
                break;
            }
            if (insidePemBlock) {
                base64.append(trimmed);
            }
        }
        return Base64.getDecoder().decode(base64.toString());
    }
}
