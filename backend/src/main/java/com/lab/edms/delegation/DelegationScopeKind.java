package com.lab.edms.delegation;

public enum DelegationScopeKind {
    ALL,           // 전 결재 단계 위임
    APPROVAL_STEP  // 특정 role_code 단계만 (scope_value = role_code)
}
