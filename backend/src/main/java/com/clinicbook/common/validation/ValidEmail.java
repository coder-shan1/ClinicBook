package com.clinicbook.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Strict email validation for stored account addresses.
 *
 * <p>{@code @Email} from Jakarta Validation is deliberately lenient and accepts values such
 * as {@code user@gmail}, {@code user@localhost} or {@code user@example.c}. ClinicBook sends
 * mail to these addresses and treats them as account identifiers, so a syntactically usable
 * mailbox is required: ASCII local part, at least one domain label, and a real TLD of two or
 * more characters (or a punycode TLD such as {@code xn--p1ai}).
 */
@Documented
@Constraint(validatedBy = ValidEmailValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidEmail {

    String message() default "must be a valid email address";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
