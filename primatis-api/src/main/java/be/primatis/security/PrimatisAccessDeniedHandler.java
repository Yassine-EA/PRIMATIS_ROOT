package be.primatis.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.time.Clock;
/**
 * Gère les refus d'accès HTTP pour les utilisateurs authentifiés.
 */
public class PrimatisAccessDeniedHandler implements AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(PrimatisAccessDeniedHandler.class);

    private final SecurityErrorResponseWriter responseWriter;

    public PrimatisAccessDeniedHandler(ObjectMapper objectMapper, Clock clock) {
        this.responseWriter = new SecurityErrorResponseWriter(objectMapper, clock);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        log.debug("Autorisation HTTP refusée sur {} {}", request.getMethod(), request.getRequestURI());
        responseWriter.write(request, response, HttpStatus.FORBIDDEN, "ACCESS_DENIED",
                "Vous n'êtes pas autorisé à effectuer cette action.");
    }
}
