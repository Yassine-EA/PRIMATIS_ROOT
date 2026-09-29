package be.primatis.user;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import org.springframework.stereotype.Component;

import java.util.Optional;
/**
 * Normalise et valide les numéros de téléphone.
 */
@Component
public class PhoneNumberNormalizer {

    private static final String DEFAULT_REGION = "BE";

    private final PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();
    public Optional<String> normalizeToE164(String rawNumber) {
        if (rawNumber == null || rawNumber.isBlank()) {
            return Optional.empty();
        }
        try {
            Phonenumber.PhoneNumber parsed = phoneNumberUtil.parse(rawNumber, DEFAULT_REGION);
            if (!phoneNumberUtil.isValidNumber(parsed)) {
                return Optional.empty();
            }
            return Optional.of(phoneNumberUtil.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164));
        } catch (NumberParseException ex) {
            return Optional.empty();
        }
    }
}
