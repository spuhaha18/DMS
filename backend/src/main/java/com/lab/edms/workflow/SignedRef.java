package com.lab.edms.workflow;

import java.time.Instant;

public record SignedRef(Long userId, String userIdString, Instant signedAt, Long manifestId) {}
