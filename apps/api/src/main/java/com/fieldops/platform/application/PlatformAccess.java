package com.fieldops.platform.application;

import java.util.UUID;
import com.fieldops.identity.application.CurrentIdentity;
import com.fieldops.tenant.application.TenantAccessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class PlatformAccess {
    private final com.fieldops.identity.application.UserService users;
    private final JdbcTemplate jdbc;
    private final CurrentIdentity identity;
    private final boolean enabled;
    public PlatformAccess(JdbcTemplate jdbc, CurrentIdentity identity, com.fieldops.identity.application.UserService users, @Value("${fieldops.platform-enabled}") boolean enabled) {
        this.users = users;
        this.jdbc = jdbc; this.identity = identity; this.enabled = enabled;
    }
    public boolean hasGrant(UUID user) {
        return enabled && users.isVerifiedActive(user) && Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM platform_role_grants g
                WHERE g.user_id = ? AND g.role = 'PLATFORM_ADMIN' AND g.status = 'ACTIVE'
                  )
                """, Boolean.class, user));
    }
    /** Lock the live grant during acceptance so an operator revocation cannot pass unnoticed. */
    public boolean lockGrant(UUID user) {
        return enabled && !jdbc.query("""
                SELECT g.id FROM platform_role_grants g
                WHERE g.user_id = ? AND g.role = 'PLATFORM_ADMIN' AND g.status = 'ACTIVE'
                  FOR SHARE OF g
                """, (rs, row) -> rs.getObject(1, UUID.class), user).isEmpty() && users.lockVerifiedActive(user);
    }
    public UUID require() {
        var user = identity.verified();
        if (!hasGrant(user.id())) throw TenantAccessException.forbidden();
        return user.id();
    }
}
