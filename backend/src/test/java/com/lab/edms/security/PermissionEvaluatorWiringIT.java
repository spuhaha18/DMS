package com.lab.edms.security;

import com.lab.edms.TestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that EdmsPermissionEvaluator is wired into Spring Security method
 * security via MethodSecurityConfig, and that the M3 stub returns false.
 */
@ActiveProfiles("test")
@SpringBootTest
@Import(TestcontainersConfig.class)
class PermissionEvaluatorWiringIT {

    @Autowired
    private MethodSecurityExpressionHandler methodSecurityExpressionHandler;

    @Autowired
    private EdmsPermissionEvaluator permissionEvaluator;

    @Test
    void methodSecurityExpressionHandler_isLoaded() {
        assertThat(methodSecurityExpressionHandler).isNotNull();
        assertThat(methodSecurityExpressionHandler)
                .isInstanceOf(DefaultMethodSecurityExpressionHandler.class);
    }

    @Test
    void edmsPermissionEvaluator_isWiredAsPermissionEvaluator() {
        assertThat(permissionEvaluator).isNotNull();
        assertThat(permissionEvaluator).isInstanceOf(EdmsPermissionEvaluator.class);
    }

    @Test
    void hasPermission_withTargetId_returnsfalse_asM3Stub() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "user", "password");
        boolean result = permissionEvaluator.hasPermission(auth, 42L, "Document", "READ");
        assertThat(result).isFalse();
    }

    @Test
    void hasPermission_withTargetObject_returnsFalse_asM3Stub() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "user", "password");
        boolean result = permissionEvaluator.hasPermission(auth, new Object(), "READ");
        assertThat(result).isFalse();
    }
}
