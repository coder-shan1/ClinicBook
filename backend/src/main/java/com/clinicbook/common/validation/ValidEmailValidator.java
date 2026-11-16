package com.clinicbook.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

/**
 * Enforces the RFC 5321/5322 shape that matters for an account email:
 *
 * <ul>
 *   <li>at most 254 characters, local part at most 64;</li>
 *   <li>exactly one {@code @};</li>
 *   <li>an ASCII dot-atom local part (no leading/trailing/consecutive dots);</li>
 *   <li>at least two domain labels, each 1–63 characters without a leading/trailing hyphen;</li>
 *   <li>a final label that is a 2–63 character alphabetic TLD or a punycode TLD.</li>
 * </ul>
 *
 * <p>Blank values are accepted here so {@code @NotBlank} owns the "required" message and
 * callers get one error per field instead of a duplicate.
 */
public class ValidEmailValidator implements ConstraintValidator<ValidEmail, CharSequence> {

    private static final int MAX_EMAIL_LENGTH = 254;
    private static final int MAX_DOMAIN_LENGTH = 253;
    private static final int MAX_LOCAL_PART_LENGTH = 64;

    private static final Pattern LOCAL_PART = Pattern.compile(
            "^[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*$");
    private static final Pattern DOMAIN_LABEL = Pattern.compile(
            "^[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?$");
    private static final Pattern ALPHABETIC_TLD = Pattern.compile("^[A-Za-z]{2,63}$");

    @Override
    public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
        if (value == null || value.toString().isBlank()) {
            return true;
        }

        String email = value.toString();
        if (email.length() > MAX_EMAIL_LENGTH) {
            return false;
        }

        int at = email.indexOf('@');
        if (at <= 0 || at != email.lastIndexOf('@') || at == email.length() - 1) {
            return false;
        }

        String localPart = email.substring(0, at);
        String domain = email.substring(at + 1);

        if (localPart.length() > MAX_LOCAL_PART_LENGTH || !LOCAL_PART.matcher(localPart).matches()) {
            return false;
        }
        if (domain.length() > MAX_DOMAIN_LENGTH) {
            return false;
        }

        String[] labels = domain.split("\\.", -1);
        if (labels.length < 2) {
            return false;
        }

        for (int i = 0; i < labels.length; i++) {
            String label = labels[i];
            if (!DOMAIN_LABEL.matcher(label).matches()) {
                return false;
            }
            if (i == labels.length - 1 && !isAcceptableTld(label)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isAcceptableTld(String label) {
        return ALPHABETIC_TLD.matcher(label).matches()
                || (label.length() > 4 && label.regionMatches(true, 0, "xn--", 0, 4));
    }
}
