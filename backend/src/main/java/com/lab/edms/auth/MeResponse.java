package com.lab.edms.auth;

import java.util.List;

public record MeResponse(
        String userId,
        String fullName,
        String email,
        String department,
        List<String> roles
) {}
