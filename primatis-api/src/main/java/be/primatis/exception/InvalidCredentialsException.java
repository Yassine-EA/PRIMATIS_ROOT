package be.primatis.exception;

/**
 * Indique qu'une tentative d'authentification a échoué.
 */
public class InvalidCredentialsException extends ApiException {

    private static final String DEFAULT_CODE = "INVALID_CREDENTIALS";

    public InvalidCredentialsException() {
        super(DEFAULT_CODE, "Identifiants invalides.");
    }
}
