package be.primatis.security;

import be.primatis.exception.ApiErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.time.Clock;
import java.util.List;
/**
 * Écrit les réponses d'erreur produites par la chaîne de sécurité.
 */
class SecurityErrorResponseWriter {

    private final ObjectMapper objectMapper;
    private final Clock clock;

    SecurityErrorResponseWriter(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String code,
            String message) throws IOException {
        ApiErrorResponse body = new ApiErrorResponse(
                clock.instant(), status.value(), status.getReasonPhrase(), code, message,
                request.getRequestURI(), List.of());

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        // chez Jackson), jamais getWriter() — ce dernier encode selon le
        // characterEncoding de la réponse, qui reste au défaut du conteneur
        // Servlet (ISO-8859-1) tant qu'aucun setCharacterEncoding("UTF-8")
        // confirmé en conditions réelles : tout message d'erreur 401
        // accentué ("expiré", "accéder") était corrompu (octet ISO-8859-1
        // brut réinterprété comme UTF-8 par le client -> U+FFFD).
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
