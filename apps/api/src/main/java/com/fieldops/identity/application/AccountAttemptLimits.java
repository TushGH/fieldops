package com.fieldops.identity.application;

import java.util.HashMap;
import java.util.Map;
import com.fieldops.tenant.application.TenantAccessException;
import org.springframework.stereotype.Component;

/** Single-instance, bounded fifteen-minute windows; complements network limits. */
@Component
public class AccountAttemptLimits {
    private record Window(long until, int count) { }
    private final Map<String, Window> windows = new HashMap<>();
    public synchronized void require(String key, int limit) {
        long now = System.currentTimeMillis();
        windows.entrySet().removeIf(entry -> entry.getValue().until() <= now);
        var window = windows.getOrDefault(key, new Window(now + 900_000, 0));
        if (window.count() >= limit || (!windows.containsKey(key) && windows.size() >= 10000)) {
            throw new TenantAccessException(429, "RATE_LIMITED", "Too many attempts. Try again in fifteen minutes.");
        }
        windows.put(key, new Window(window.until(), window.count() + 1));
    }
}
