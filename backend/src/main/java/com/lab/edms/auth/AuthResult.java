package com.lab.edms.auth;

import com.lab.edms.user.User;

public sealed interface AuthResult {
    record Success(User user) implements AuthResult {}
    record InvalidCredentials(int remainingAttempts) implements AuthResult {}
    record AccountLocked() implements AuthResult {}
    record AccountDisabled() implements AuthResult {}
    record ForcePasswordChange(User user) implements AuthResult {}
}
