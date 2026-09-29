package be.primatis.exception;

/**
 * Indique qu'une règle métier empêche l'opération demandée.
 */
public class BusinessRuleException extends ApiException {

    private static final String DEFAULT_CODE = "BUSINESS_RULE_VIOLATION";

    public BusinessRuleException(String message) {
        super(DEFAULT_CODE, message);
    }

    public BusinessRuleException(String code, String message) {
        super(code, message);
    }
}
