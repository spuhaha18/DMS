package com.lab.edms.workflow;

import java.time.Instant;

public record AssigneeRef(Long userId, String userIdString, Instant fixedAt, String fixedBy) {}
