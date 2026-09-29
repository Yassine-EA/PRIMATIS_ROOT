package be.primatis.exception;

/**
 * Indique qu'un intervalle de dates fourni dans une requête est incohérent.
 */
public class InvalidDateRangeException extends ApiException {

    private static final String CODE = "INVALID_DATE_RANGE";

    private final String parameterName;

    public InvalidDateRangeException(String parameterName, String message) {
        super(CODE, message);
        this.parameterName = parameterName;
    }

    public String getParameterName() {
        return parameterName;
    }
}
