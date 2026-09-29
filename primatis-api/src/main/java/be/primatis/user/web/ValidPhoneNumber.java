package be.primatis.user.web;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PhoneNumberConstraintValidator.class)
/**
 * Valide le format d'un numéro de téléphone lorsqu'il est renseigné.
 */
public @interface ValidPhoneNumber {

    String message() default "Numéro de téléphone invalide.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
