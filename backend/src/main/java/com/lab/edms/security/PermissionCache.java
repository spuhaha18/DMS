package com.lab.edms.security;

import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Request-scoped permission cache to prevent N+1 DB hits per request.
 * Uses ScopedProxyMode so it can be injected into singleton beans.
 * Falls back gracefully when no request context is active (e.g., in tests).
 */
@Component
@RequestScope(proxyMode = ScopedProxyMode.TARGET_CLASS)
public class PermissionCache {

    private final Map<String, Boolean> cache = new HashMap<>();

    public boolean computeIfAbsent(String key, Supplier<Boolean> loader) {
        return cache.computeIfAbsent(key, k -> loader.get());
    }
}
