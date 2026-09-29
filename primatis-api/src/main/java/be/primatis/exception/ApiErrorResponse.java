package be.primatis.exception;

import java.time.Instant;
import java.util.List;

/**
 * Représentation standard d'une erreur retournée par l'API.
 */
public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        List<ApiFieldError> fieldErrors) {
}
