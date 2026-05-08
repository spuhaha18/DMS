package com.lab.edms.audit;

public enum AuditAction {
    USER_LOGIN_SUCCESS,
    USER_LOGIN_FAIL,
    USER_LOGOUT,
    USER_PASSWORD_CHANGED,
    USER_LOCKED,
    USER_UNLOCKED,
    USER_FORCED_CHANGE_PW
}
