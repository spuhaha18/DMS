package com.lab.edms.auth.dto;

public record SessionStateDto(String userId, String userName, boolean firstSignRequired) {}
