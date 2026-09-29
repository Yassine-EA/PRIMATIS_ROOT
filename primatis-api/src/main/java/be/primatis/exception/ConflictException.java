package be.primatis.exception;

/**
 * Indique un conflit empêchant l'exécution de l'opération demandée.
 */
public class ConflictException extends ApiException {

    private static final String DEFAULT_CODE = "CONFLICT";

    public ConflictException(String message) {
        super(DEFAULT_CODE, message);
    }

    public ConflictException(String code, String message) {
        super(code, message);
    }
}
