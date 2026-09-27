package com.fieldops.tenant.infrastructure;

import java.util.Collections;
import java.util.UUID;

import com.fieldops.identity.application.UserService;
import com.fieldops.identity.domain.UserStatus;
import com.fieldops.tenant.application.TenantAccessException;
import com.fieldops.tenant.domain.MembershipRole;
import com.fieldops.tenant.domain.MembershipStatus;
import com.fieldops.tenant.domain.TenantStatus;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

/** A validated snapshot for exactly one request; never stored in the login session. */
@Component
@RequestScope
public class AuthenticatedTenantContext {
    private final HttpServletRequest request;
    private final UserService users;
    private final TenantRepository tenants;
    private final MembershipRepository memberships;
    private Selection selection;

    public AuthenticatedTenantContext(HttpServletRequest request, UserService users,
                                      TenantRepository tenants, MembershipRepository memberships) {
        this.request = request;
        this.users = users;
        this.tenants = tenants;
        this.memberships = memberships;
    }

    public Selection require() {
        if (selection != null) return selection;
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            throw new AuthenticationCredentialsNotFoundException("Authentication required");
        }
        var userId = UUID.fromString(authentication.getName());
        if (users.findById(userId).filter(user -> user.status() == UserStatus.ACTIVE).isEmpty()) {
            throw new AuthenticationCredentialsNotFoundException("Active user required");
        }
        var headers = Collections.list(request.getHeaders("X-Tenant-ID"));
        if (headers.isEmpty()) {
            throw new TenantAccessException(400, "TENANT_REQUIRED", "Select a tenant with X-Tenant-ID.");
        }
        final UUID tenantId;
        try {
            if (headers.size() != 1) throw new IllegalArgumentException();
            tenantId = UUID.fromString(headers.getFirst());
            if (!tenantId.toString().equalsIgnoreCase(headers.getFirst())) throw new IllegalArgumentException();
        } catch (IllegalArgumentException exception) {
            throw new TenantAccessException(400, "INVALID_TENANT", "X-Tenant-ID must contain exactly one UUID.");
        }
        var membership = memberships.findAccess(tenantId, userId, MembershipStatus.ACTIVE)
                .orElseThrow(TenantAccessException::forbidden);
        if (!tenants.existsByIdAndStatus(tenantId, TenantStatus.ACTIVE)) throw TenantAccessException.forbidden();
        selection = new Selection(userId, tenantId, membership.getId(), membership.getRole());
        return selection;
    }

    public record Selection(UUID userId, UUID tenantId, UUID membershipId, MembershipRole role) { }
}
