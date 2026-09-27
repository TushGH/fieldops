package com.fieldops.identity.infrastructure;

import java.io.IOException;
import java.util.UUID;

import com.fieldops.identity.application.UserService;
import com.fieldops.identity.domain.UserStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Recheck global account state on each request using an existing session. */
final class ActiveUserSessionFilter extends OncePerRequestFilter {
    private final UserService users;

    ActiveUserSessionFilter(UserService users) {
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            var active = users.findById(UUID.fromString(authentication.getName()))
                    .filter(user -> user.status() == UserStatus.ACTIVE).isPresent();
            if (!active) {
                var session = request.getSession(false);
                if (session != null) session.invalidate();
                SecurityContextHolder.clearContext();
                AuthenticationErrors.unauthenticated(response);
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
