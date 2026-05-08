package com.lab.edms.auth;

public record LoginResponse(
        String userId,
        String fullName,
        boolean forceChangePw
) {}
