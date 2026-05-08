package com.lab.edms.auth;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PasswordPolicyValidatorTest {

    @Test
    void shorterThan8_isTooShort() {
        assertThat(PasswordPolicyValidator.validate("Ab1!"))
                .isInstanceOf(PasswordPolicyValidator.Result.TooShort.class);
    }

    @Test
    void null_isTooShort() {
        assertThat(PasswordPolicyValidator.validate(null))
                .isInstanceOf(PasswordPolicyValidator.Result.TooShort.class);
    }

    @Test
    void onlyLowercase_isInsufficient() {
        assertThat(PasswordPolicyValidator.validate("abcdefgh"))
                .isInstanceOf(PasswordPolicyValidator.Result.InsufficientCharacterClasses.class);
    }

    @Test
    void lowercaseAndUppercase_isInsufficient() {
        assertThat(PasswordPolicyValidator.validate("Abcdefgh"))
                .isInstanceOf(PasswordPolicyValidator.Result.InsufficientCharacterClasses.class);
    }

    @Test
    void threeClasses_lower_upper_digit_isOk() {
        assertThat(PasswordPolicyValidator.validate("Abcdefg1"))
                .isInstanceOf(PasswordPolicyValidator.Result.Ok.class);
    }

    @Test
    void threeClasses_lower_upper_special_isOk() {
        assertThat(PasswordPolicyValidator.validate("Abcdefg!"))
                .isInstanceOf(PasswordPolicyValidator.Result.Ok.class);
    }

    @Test
    void threeClasses_lower_digit_special_isOk() {
        assertThat(PasswordPolicyValidator.validate("abcdef1!"))
                .isInstanceOf(PasswordPolicyValidator.Result.Ok.class);
    }

    @Test
    void fourClasses_isOk() {
        assertThat(PasswordPolicyValidator.validate("Abcdef1!"))
                .isInstanceOf(PasswordPolicyValidator.Result.Ok.class);
    }
}
