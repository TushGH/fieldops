package com.fieldops.identity.application;

import java.util.UUID;
import com.fieldops.identity.domain.UserStatus;
import com.fieldops.tenant.application.TenantAccessException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CurrentIdentity {
    private final UserService users;
    public CurrentIdentity(UserService users) { this.users = users; }

    public UserDetails require() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new AuthenticationCredentialsNotFoundException("Authentication required");
        }
        return users.findById(UUID.fromString(auth.getName()))
                .filter(user -> user.status() == UserStatus.ACTIVE)
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("Active account required"));
    }

    public UserDetails verified() {
        var user = require();
        if (user.emailVerifiedAt() == null) {
            throw new TenantAccessException(403, "EMAIL_VERIFICATION_REQUIRED", "Verify your email before continuing.");
        }
        return user;
    }
}
