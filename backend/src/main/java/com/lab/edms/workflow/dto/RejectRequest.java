package com.lab.edms.workflow.dto;

public record RejectRequest(Long stepInstanceId, String password, String reason) {}
