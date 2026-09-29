package be.primatis.user;

import be.primatis.exception.BusinessRuleException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
/**
 * Génère les numéros d'adhérent.
 */
@Component
public class MemberNumberGenerator {

    private static final int MEMBER_NUMBER_DIGITS = 9;
    private static final long MAX_MEMBER_NUMBER = 999_999_999L;

    @PersistenceContext
    private EntityManager entityManager;
    public String generateNext() {
        Number nextValue = (Number) entityManager
                .createNativeQuery("SELECT nextval('member_number_seq')")
                .getSingleResult();
        long value = nextValue.longValue();
        if (value > MAX_MEMBER_NUMBER) {
            throw new BusinessRuleException(
                    "MEMBER_NUMBER_SEQUENCE_EXHAUSTED",
                    "La séquence de numéros d'adhérent a atteint sa capacité maximale.");
        }
        return "M" + String.format("%0" + MEMBER_NUMBER_DIGITS + "d", value);
    }
}
