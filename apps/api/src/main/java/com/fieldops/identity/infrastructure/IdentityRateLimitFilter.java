package com.fieldops.identity.infrastructure;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import com.fieldops.identity.domain.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

/** Bounded, single-instance abuse control. Never trusts caller-supplied forwarding headers. */
public class IdentityRateLimitFilter extends OncePerRequestFilter {
    private record Window(long until, int count) { }
    private final Map<String, Window> windows = new HashMap<>();
    private final long interval = Duration.ofMinutes(15).toMillis();

    public synchronized boolean allow(String key, int limit) {
        long now = System.currentTimeMillis();
        windows.entrySet().removeIf(entry -> entry.getValue().until() <= now);
        var window = windows.get(key);
        if (window == null) {
            if (windows.size() >= 10000) return false;
            window = new Window(now + interval, 0);
        }
        if (window.count() >= limit) return false;
        windows.put(key, new Window(window.until(), window.count() + 1));
        return true;
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var path = request.getRequestURI();
        if (request.getMethod().equals("POST") && (path.startsWith("/api/v1/auth/")
                || path.contains("invitations") || path.startsWith("/api/v1/platform/"))) {
            boolean allowed = allow("network:" + request.getRemoteAddr(), 100);
            if (path.equals("/api/v1/auth/login")) {
                try { allowed &= allow("login:" + User.normalizeEmail(request.getParameter("email")), 20); }
                catch (IllegalArgumentException ignored) { allowed = false; }
            }
            if (!allowed) {
                response.setStatus(429);
                response.setHeader("Retry-After", "900");
                response.setContentType("application/json");
                response.getWriter().write("{\"code\":\"RATE_LIMITED\",\"message\":\"Too many attempts. Try again later.\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
