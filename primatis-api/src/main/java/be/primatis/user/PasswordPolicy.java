package be.primatis.user;

import java.util.Locale;
import java.util.Set;
/**
 * Applique la politique de validation des mots de passe.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 15;

    // NOTE : MIN_LENGTH (15) filtre déjà la plupart des mots de passe
    // courants classiques (généralement < 12 caractères). Cette liste inclut
    // donc volontairement des variantes "longues" réellement observées dans
    // les compilations publiques de fuites (mots de passe courants étirés ou
    // "keyboard walks" longs), pour que le rejet par liste reste une règle
    // effective et pas seulement théorique une fois la longueur minimale
    // franchie.
    private static final Set<String> COMMON_PASSWORDS = Set.of(
            "password", "password1", "password123",
            "123456", "1234567", "12345678", "123456789", "1234567890",
            "qwerty", "qwertyuiop", "azerty",
            "letmein", "iloveyou", "welcome", "welcome123",
            "admin", "admin123", "changeme",
            "motdepasse", "motdepasse1", "bienvenue123",
            "password1234567", "password12345678",
            "1qaz2wsx3edc4rfv", "qwertyuiopasdfghjkl",
            "iloveyou12345678");

    private PasswordPolicy() {
    }
    public static ValidationResult validate(String rawPassword) {
        if (rawPassword == null || rawPassword.isEmpty()) {
            return ValidationResult.rejected("Le mot de passe est obligatoire.");
        }
        if (rawPassword.length() < MIN_LENGTH) {
            return ValidationResult.rejected(
                    "Le mot de passe doit contenir au moins " + MIN_LENGTH + " caractères.");
        }
        if (isCommon(rawPassword)) {
            return ValidationResult.rejected("Ce mot de passe est trop courant ou prévisible.");
        }
        return ValidationResult.accepted();
    }

    private static boolean isCommon(String rawPassword) {
        return COMMON_PASSWORDS.contains(rawPassword.toLowerCase(Locale.ROOT));
    }
    public record ValidationResult(boolean valid, String reason) {

        public static ValidationResult accepted() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult rejected(String reason) {
            return new ValidationResult(false, reason);
        }
    }
}
