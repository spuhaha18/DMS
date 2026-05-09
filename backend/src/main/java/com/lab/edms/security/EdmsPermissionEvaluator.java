package com.lab.edms.security;

import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.io.Serializable;

@Component
public class EdmsPermissionEvaluator implements PermissionEvaluator {

    @Override
    public boolean hasPermission(Authentication auth, Object target, Object permission) {
        if (auth == null || !auth.isAuthenticated()) return false;
        if (!(permission instanceof String)) return false;
        // M2 stub: doc-level checks come in M3
        return false;
    }

    @Override
    public boolean hasPermission(Authentication auth, Serializable id, String type, Object permission) {
        return false; // M3+ implements doc-level lookup
    }

    public static boolean hasRole(Authentication auth, String roleCode) {
        if (auth == null) return false;
        String wanted = "ROLE_" + roleCode;
        for (GrantedAuthority ga : auth.getAuthorities()) {
            if (wanted.equals(ga.getAuthority())) return true;
        }
        return false;
    }
}
