package be.primatis.exception;

/**
 * Indique qu'un utilisateur authentifié n'est pas autorisé à effectuer une opération.
 */
public class ForbiddenOperationException extends ApiException {

    private static final String DEFAULT_CODE = "FORBIDDEN_OPERATION";

    public ForbiddenOperationException(String message) {
        super(DEFAULT_CODE, message);
    }

    public ForbiddenOperationException(String code, String message) {
        super(code, message);
    }
}
