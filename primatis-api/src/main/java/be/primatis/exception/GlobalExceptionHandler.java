package be.primatis.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.List;

/**
 * Traduit de manière centralisée les exceptions de l'application en réponses d'erreur API.
 *
 * Les détails internes ne sont pas exposés au client et les erreurs inattendues
 * sont journalisées côté serveur.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleResourceNotFound(
            ResourceNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getCode(), ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(ConflictException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getCode(), ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiErrorResponse> handleBusinessRule(
            BusinessRuleException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getCode(), ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(ForbiddenOperationException.class)
    public ResponseEntity<ApiErrorResponse> handleForbidden(
            ForbiddenOperationException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, ex.getCode(), ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidCredentials(
            InvalidCredentialsException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, ex.getCode(), ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(AccountTemporarilyLockedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccountTemporarilyLocked(
            AccountTemporarilyLockedException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, ex.getCode(), ex.getMessage(), request, List.of());
    }

    /**
     * Traduit les refus d'autorisation issus de la sécurité des méthodes.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "ACCESS_DENIED",
                "Vous n'êtes pas autorisé à effectuer cette action.", request, List.of());
    }

    @ExceptionHandler(InvalidDateRangeException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidDateRange(
            InvalidDateRangeException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getCode(), ex.getMessage(), request,
                List.of(new ApiFieldError(ex.getParameterName(), ex.getMessage())));
    }

    @ExceptionHandler(InvalidMediaFileException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidMediaFile(
            InvalidMediaFileException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getCode(), ex.getMessage(), request, List.of());
    }

    /**
     * Traite les fichiers dépassant la taille maximale acceptée par l'application.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleMaxUploadSizeExceeded(
            MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "ARTICLE_MEDIA_FILE_TOO_LARGE",
                "Le fichier dépasse la taille maximale autorisée (5 Mio).", request, List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ApiFieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> new ApiFieldError(fieldError.getField(), fieldError.getDefaultMessage()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "Un ou plusieurs champs sont invalides.", request, fieldErrors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {
        List<ApiFieldError> fieldErrors = ex.getConstraintViolations().stream()
                .map(violation -> new ApiFieldError(lastPathNode(violation.getPropertyPath().toString()),
                        violation.getMessage()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, "CONSTRAINT_VIOLATION",
                "Un ou plusieurs paramètres sont invalides.", request, fieldErrors);
    }

    /**
     * Traite les paramètres qui ne peuvent pas être convertis vers le type attendu.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        List<ApiFieldError> fieldErrors =
                List.of(new ApiFieldError(ex.getName(), "Valeur invalide pour ce paramètre."));
        return build(HttpStatus.BAD_REQUEST, "INVALID_REQUEST_PARAMETER",
                "Un ou plusieurs paramètres sont invalides.", request, fieldErrors);
    }

    /**
     * Traite les corps de requête illisibles ou impossibles à désérialiser.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST_BODY",
                "Le corps de la requête est invalide ou illisible.", request, List.of());
    }

    /**
     * Traite les requêtes qui ne correspondent à aucune route disponible.
     */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ApiErrorResponse> handleNoRouteFound(Exception ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "ROUTE_NOT_FOUND",
                "Aucune route ne correspond à cette requête.", request, List.of());
    }

    // Erreurs de protocole HTTP

    /** Traite une méthode HTTP non supportée et renseigne l'en-tête Allow lorsqu'il est disponible. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        log.debug("Méthode HTTP non supportée : {} {}", request.getMethod(), request.getRequestURI());
        ResponseEntity<ApiErrorResponse> response = build(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",
                "Cette méthode HTTP n'est pas supportée sur cette ressource.", request, List.of());
        if (ex.getSupportedHttpMethods() == null || ex.getSupportedHttpMethods().isEmpty()) {
            return response;
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setAllow(ex.getSupportedHttpMethods());
        return ResponseEntity.status(response.getStatusCode()).headers(headers).body(response.getBody());
    }

    /** Traite un type de contenu non supporté par la route. */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        log.debug("Content-Type non supporté : {} {}", request.getMethod(), request.getRequestURI());
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE",
                "Le format du contenu envoyé (Content-Type) n'est pas supporté.", request, List.of());
    }

    /** Traite un format de réponse incompatible avec l'en-tête Accept du client. */
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ApiErrorResponse> handleMediaTypeNotAcceptable(
            HttpMediaTypeNotAcceptableException ex, HttpServletRequest request) {
        log.debug("Accept non satisfaisable : {} {}", request.getMethod(), request.getRequestURI());
        ResponseEntity<ApiErrorResponse> response = build(HttpStatus.NOT_ACCEPTABLE, "NOT_ACCEPTABLE",
                "Aucun format de réponse acceptable n'est disponible pour cette ressource.", request, List.of());
        // Force une réponse JSON lorsque la négociation de contenu ne peut aboutir.
        return ResponseEntity.status(response.getStatusCode())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(response.getBody());
    }

    /** Traite l'absence d'une partie multipart obligatoire. */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingRequestPart(
            MissingServletRequestPartException ex, HttpServletRequest request) {
        log.debug("Partie multipart absente ({}) : {} {}", ex.getRequestPartName(), request.getMethod(),
                request.getRequestURI());
        return build(HttpStatus.BAD_REQUEST, "MISSING_REQUEST_PART",
                "Une partie obligatoire de la requête est absente.", request,
                List.of(new ApiFieldError(ex.getRequestPartName(), "Cette partie est obligatoire.")));
    }

    /** Traite l'absence d'un paramètre de requête obligatoire. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingRequestParameter(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        log.debug("Paramètre requis absent ({}) : {} {}", ex.getParameterName(), request.getMethod(),
                request.getRequestURI());
        return build(HttpStatus.BAD_REQUEST, "MISSING_REQUEST_PARAMETER",
                "Un paramètre obligatoire de la requête est absent.", request,
                List.of(new ApiFieldError(ex.getParameterName(), "Ce paramètre est obligatoire.")));
    }

    /**
     * Traite les erreurs inattendues sans exposer leurs détails au client.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Erreur interne inattendue sur {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Une erreur interne est survenue.", request, List.of());
    }

    private ResponseEntity<ApiErrorResponse> build(
            HttpStatus status, String code, String message, HttpServletRequest request,
            List<ApiFieldError> fieldErrors) {
        ApiErrorResponse body = new ApiErrorResponse(
                Instant.now(), status.value(), status.getReasonPhrase(), code, message,
                request.getRequestURI(), fieldErrors);
        return ResponseEntity.status(status).body(body);
    }

    private String lastPathNode(String propertyPath) {
        int lastDot = propertyPath.lastIndexOf('.');
        return lastDot >= 0 ? propertyPath.substring(lastDot + 1) : propertyPath;
    }
}
