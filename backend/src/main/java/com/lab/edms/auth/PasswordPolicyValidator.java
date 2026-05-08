package com.lab.edms.auth;

public final class PasswordPolicyValidator {

    public sealed interface Result {
        record Ok() implements Result {}
        record TooShort() implements Result {}
        record InsufficientCharacterClasses() implements Result {}
    }

    private PasswordPolicyValidator() {}

    /** FS-AUTH-002: min 8 chars, at least 3 of: lowercase, uppercase, digit, special. */
    public static Result validate(String pw) {
        if (pw == null || pw.length() < 8) return new Result.TooShort();
        int classes = 0;
        if (pw.matches(".*[a-z].*")) classes++;
        if (pw.matches(".*[A-Z].*")) classes++;
        if (pw.matches(".*\\d.*"))   classes++;
        if (pw.matches(".*[^A-Za-z0-9].*")) classes++;
        if (classes < 3) return new Result.InsufficientCharacterClasses();
        return new Result.Ok();
    }
}
