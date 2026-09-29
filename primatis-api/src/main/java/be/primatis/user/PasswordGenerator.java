package be.primatis.user;

import java.security.SecureRandom;
/**
 * Génère les mots de passe initiaux des utilisateurs.
 */
public final class PasswordGenerator {

    private static final int GENERATED_LENGTH = 24;

    private static final String ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789!@#$%&*-_";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private PasswordGenerator() {
    }

    public static String generate() {
        StringBuilder generated = new StringBuilder(GENERATED_LENGTH);
        for (int i = 0; i < GENERATED_LENGTH; i++) {
            generated.append(ALPHABET.charAt(SECURE_RANDOM.nextInt(ALPHABET.length())));
        }
        String candidate = generated.toString();

        PasswordPolicy.ValidationResult validation = PasswordPolicy.validate(candidate);
        if (!validation.valid()) {
            throw new IllegalStateException(
                    "Le générateur de mot de passe a produit une valeur non conforme à PasswordPolicy.");
        }
        return candidate;
    }
}
