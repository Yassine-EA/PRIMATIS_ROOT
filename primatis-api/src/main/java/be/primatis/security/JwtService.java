package be.primatis.security;

import be.primatis.config.JwtProperties;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
/**
 * Service chargé de générer les jetons d'accès JWT.
 */
@Service
public class JwtService {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;
    private final Clock clock;

    public JwtService(JwtEncoder jwtEncoder, JwtProperties jwtProperties, Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.jwtProperties = jwtProperties;
        this.clock = clock;
    }

    /**
     * Génère un jeton d'accès pour une authentification validée.
     *
     * @param authentication authentification validée
     * @return jeton d'accès généré
     */
    public AccessToken generateAccessToken(Authentication authentication) {
        PrimatisUserPrincipal principal = (PrimatisUserPrincipal) authentication.getPrincipal();

        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(jwtProperties.accessTokenTtl());
        // Les rôles et permissions sont conservés dans des claims distincts.
        List<String> roles = principal.getRoles();
        List<String> permissions = principal.getPermissions();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .audience(List.of(jwtProperties.audience()))
                .subject(String.valueOf(principal.getUserId()))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("roles", roles)
                .claim("permissions", permissions)
                .build();

        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        Jwt jwt = jwtEncoder.encode(JwtEncoderParameters.from(header, claims));

        return new AccessToken(
                jwt.getTokenValue(), "Bearer", expiresAt, jwtProperties.accessTokenTtl().getSeconds());
    }
}
