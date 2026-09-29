package be.primatis.exception;

/**
 * Indique qu'un compte est temporairement verrouillé après plusieurs échecs de connexion.
 */
public class AccountTemporarilyLockedException extends ApiException {

    private static final String DEFAULT_CODE = "ACCOUNT_TEMPORARILY_LOCKED";

    public AccountTemporarilyLockedException() {
        super(DEFAULT_CODE, "Compte temporairement verrouillé suite à plusieurs échecs de connexion.");
    }
}
