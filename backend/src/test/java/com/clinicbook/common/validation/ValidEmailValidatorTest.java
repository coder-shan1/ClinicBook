package com.clinicbook.common.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ValidEmailValidatorTest {

    private final ValidEmailValidator validator = new ValidEmailValidator();

    @ParameterizedTest
    @ValueSource(strings = {
            "jane@example.com",
            "jane.doe+tag@sub.example.co.uk",
            "JANE@EXAMPLE.COM",
            "a@b.co",
            "user_name@example.io",
            "o'brien@example.com",
            "user@example.xn--p1ai",
            "user@example.travel",
            "first.last@my-domain.example"
    })
    void acceptsUsableEmailAddresses(String email) {
        assertThat(validator.isValid(email, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "user@gmail",            // missing TLD — the reported bug
            "user@localhost",        // single-label domain
            "user@com",              // single-label domain
            "user@example.c",        // one-character TLD
            "user@example.123",      // numeric TLD
            "user@example.c0m",      // digit in TLD
            "user@.com",             // empty first label
            "user@com.",             // trailing dot
            "user@example..com",     // empty label
            "user@-example.com",     // label starts with hyphen
            "user@example-.com",     // label ends with hyphen
            "user@example_com",      // underscore in domain
            "user@exa mple.com",     // whitespace in domain
            "user@@example.com",     // duplicate @
            "@example.com",          // empty local part
            "user@",                 // empty domain
            "plainaddress",
            ".user@example.com",     // leading dot in local part
            "user.@example.com",     // trailing dot in local part
            "user..name@example.com",// consecutive dots in local part
            "user name@example.com", // whitespace in local part
            "user@[127.0.0.1]",      // IP literal
            "用户@example.com",       // non-ASCII local part
            "user@example.com\n",    // control character
            "user@example.com\r"
    })
    void rejectsUnusableEmailAddresses(String email) {
        assertThat(validator.isValid(email, null)).isFalse();
    }

    @Test
    void acceptsNullOrBlankBecauseNotBlankOwnsTheRequiredMessage() {
        assertThat(validator.isValid(null, null)).isTrue();
        assertThat(validator.isValid("", null)).isTrue();
        assertThat(validator.isValid("   ", null)).isTrue();
    }

    @Test
    void enforcesTotalLengthAndLocalPartLimits() {
        String localPart64 = "a".repeat(64);
        String localPart65 = "a".repeat(65);

        assertThat(validator.isValid(localPart64 + "@example.com", null)).isTrue();
        assertThat(validator.isValid(localPart65 + "@example.com", null)).isFalse();

        // 64-char local part + "@" + 189-char domain = the RFC maximum of 254 characters.
        String domain189 = "b".repeat(63) + "." + "b".repeat(63) + "." + "b".repeat(57) + ".com";
        String domain190 = "b".repeat(63) + "." + "b".repeat(63) + "." + "b".repeat(58) + ".com";
        assertThat(domain189).hasSize(189);
        assertThat(domain190).hasSize(190);
        assertThat(validator.isValid(localPart64 + "@" + domain189, null)).isTrue();
        assertThat(validator.isValid(localPart64 + "@" + domain190, null)).isFalse();
    }

    @Test
    void enforcesDomainLabelLengthLimit() {
        String label63 = "a".repeat(63);
        String label64 = "a".repeat(64);

        assertThat(validator.isValid("user@" + label63 + ".com", null)).isTrue();
        assertThat(validator.isValid("user@" + label64 + ".com", null)).isFalse();
    }
}
