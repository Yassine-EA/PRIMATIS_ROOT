package be.primatis.user.web;

import be.primatis.user.PhoneNumberNormalizer;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
/**
 * Validateur de contrainte pour les numéros de téléphone.
 */
public class PhoneNumberConstraintValidator implements ConstraintValidator<ValidPhoneNumber, String> {

    private final PhoneNumberNormalizer phoneNumberNormalizer;

    public PhoneNumberConstraintValidator(PhoneNumberNormalizer phoneNumberNormalizer) {
        this.phoneNumberNormalizer = phoneNumberNormalizer;
    }
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        return phoneNumberNormalizer.normalizeToE164(value).isPresent();
    }
}
