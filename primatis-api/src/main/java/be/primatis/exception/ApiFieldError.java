package be.primatis.exception;

/**
 * Représente une erreur de validation associée à un champ.
 */
public record ApiFieldError(String field, String message) {
}
