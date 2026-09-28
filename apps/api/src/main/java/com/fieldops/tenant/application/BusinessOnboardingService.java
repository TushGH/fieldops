package com.fieldops.tenant.application;

import java.util.List;
import java.util.UUID;
import com.fieldops.audit.SecurityAudit;
import com.fieldops.identity.application.CurrentIdentity;
import com.fieldops.tenant.domain.Membership;
import com.fieldops.tenant.domain.MembershipRole;
import com.fieldops.tenant.domain.Tenant;
import com.fieldops.tenant.infrastructure.AuthenticatedTenantContext;
import com.fieldops.tenant.infrastructure.MembershipRepository;
import com.fieldops.tenant.infrastructure.TenantRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BusinessOnboardingService {
    private final CurrentIdentity identity;
    private final AuthenticatedTenantContext context;
    private final TenantRepository tenants;
    private final MembershipRepository memberships;
    private final JdbcTemplate jdbc;
    private final SecurityAudit audit;
    public BusinessOnboardingService(CurrentIdentity identity, AuthenticatedTenantContext context, TenantRepository tenants,
                                     MembershipRepository memberships, JdbcTemplate jdbc, SecurityAudit audit) {
        this.identity = identity; this.context = context; this.tenants = tenants;
        this.memberships = memberships; this.jdbc = jdbc; this.audit = audit;
    }

    public List<Business> list() {
        var user = identity.verified();
        return jdbc.query("""
                SELECT t.id, t.name, t.slug, m.role FROM tenants t JOIN memberships m ON m.tenant_id = t.id
                WHERE m.user_id = ? AND m.status = 'ACTIVE' AND t.status = 'ACTIVE' ORDER BY t.name, t.id
                """, (rs, row) -> new Business(rs.getObject("id", UUID.class), rs.getString("name"),
                rs.getString("slug"), rs.getString("role")), user.id());
    }

    @Transactional
    public Business create(String name, String slug) {
        var user = identity.verified();
        var tenant = tenants.saveAndFlush(Tenant.create(name, slug));
        memberships.saveAndFlush(Membership.create(tenant.getId(), user.id(), MembershipRole.BUSINESS_OWNER));
        jdbc.update("INSERT INTO business_onboarding(tenant_id, completed_version, version) VALUES (?, 0, 0)", tenant.getId());
        audit.record(user.id(), tenant.getId(), "BUSINESS_CREATED", "SUCCESS", tenant.getId());
        return new Business(tenant.getId(), tenant.getName(), tenant.getSlug(), "BUSINESS_OWNER");
    }

    public Readiness readiness() {
        var selection = context.require();
        int completed = jdbc.query("SELECT completed_version FROM business_onboarding WHERE tenant_id = ?",
                (rs, row) -> rs.getInt(1), selection.tenantId()).stream().findFirst().orElse(0);
        // Version 1 requires only the implemented business identity and an active owner.
        // No technician/dispatcher checklist exists yet, so these roles have no setup gate.
        return new Readiness(completed >= 1, 1, selection.role() == MembershipRole.BUSINESS_OWNER && completed < 1,
                List.of("Business name", "Business slug", "Active business owner"), List.of());
    }

    @Transactional
    public Readiness complete() {
        var selected = context.require();
        tenants.lockById(selected.tenantId()).orElseThrow(TenantAccessException::notFound);
        if (!Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM tenants t JOIN memberships m ON m.tenant_id = t.id
                WHERE t.id = ? AND t.status = 'ACTIVE' AND m.user_id = ? AND m.status = 'ACTIVE'
                  AND m.role = 'BUSINESS_OWNER' AND length(t.name) > 0 AND length(t.slug) >= 3)
                """, Boolean.class, selected.tenantId(), selected.userId()))) throw TenantAccessException.forbidden();
        jdbc.update("""
                INSERT INTO business_onboarding(tenant_id, completed_version, completed_at, version) VALUES (?, 1, now(), 0)
                ON CONFLICT (tenant_id) DO UPDATE SET completed_version = 1, completed_at = now(),
                    version = business_onboarding.version + 1 WHERE business_onboarding.completed_version < 1
                """, selected.tenantId());
        audit.record(selected.userId(), selected.tenantId(), "BUSINESS_ONBOARDING_COMPLETED", "SUCCESS", selected.tenantId());
        return readiness();
    }
    public record Business(UUID id, String name, String slug, String role) { }
    public record Readiness(boolean businessComplete, int requiredVersion, boolean businessSetupRequired,
                            List<String> businessChecklist, List<String> membershipFlows) { }
}
