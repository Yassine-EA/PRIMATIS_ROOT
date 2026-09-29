package be.primatis.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;
import java.time.Clock;
/**
 * Gère les jetons Bearer invalides ou refusés par le serveur de ressources.
 */
public class PrimatisInvalidTokenAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(PrimatisInvalidTokenAuthenticationEntryPoint.class);

    private final SecurityErrorResponseWriter responseWriter;

    public PrimatisInvalidTokenAuthenticationEntryPoint(ObjectMapper objectMapper, Clock clock) {
        this.responseWriter = new SecurityErrorResponseWriter(objectMapper, clock);
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        // DEBUG, pas ERROR : un token expiré/invalide est un rejet attendu du
        // workflow, pas une erreur serveur. Jamais le JWT complet journalisé.
        log.debug("Bearer token rejeté sur {} {} : {}", request.getMethod(), request.getRequestURI(),
                authException.getClass().getSimpleName());
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\"");
        responseWriter.write(request, response, HttpStatus.UNAUTHORIZED, "INVALID_TOKEN",
                "Le jeton d'authentification fourni est invalide ou a expiré.");
    }
}
