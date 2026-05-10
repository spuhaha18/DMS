package com.lab.edms.workflow.dto;

public record SignRequest(Long stepInstanceId, String password, String meaning) {}
