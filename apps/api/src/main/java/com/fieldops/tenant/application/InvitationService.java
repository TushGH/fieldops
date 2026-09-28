package com.fieldops.tenant.application;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.fieldops.audit.SecurityAudit;
import com.fieldops.identity.application.CurrentIdentity;
import com.fieldops.identity.domain.User;
import com.fieldops.identity.infrastructure.LinkTokens;
import com.fieldops.notification.AccountMail;
import com.fieldops.platform.application.PlatformAccess;
import com.fieldops.tenant.domain.Membership;
import com.fieldops.tenant.domain.MembershipRole;
import com.fieldops.tenant.domain.MembershipStatus;
import com.fieldops.tenant.domain.Tenant;
import com.fieldops.tenant.infrastructure.AuthenticatedTenantContext;
import com.fieldops.tenant.infrastructure.MembershipRepository;
import com.fieldops.tenant.infrastructure.TenantRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class InvitationService {
    private final com.fieldops.identity.application.AccountAttemptLimits limits;
    private final com.fieldops.identity.application.UserService users;
    private final JdbcTemplate jdbc;
    private final CurrentIdentity identity;
    private final AuthenticatedTenantContext context;
    private final TenantRepository tenants;
    private final MembershipRepository memberships;
    private final PlatformAccess platform;
    private final AccountMail mail;
    private final SecurityAudit audit;
    public InvitationService(JdbcTemplate jdbc, CurrentIdentity identity, AuthenticatedTenantContext context,
                             TenantRepository tenants, MembershipRepository memberships, PlatformAccess platform,
                             AccountMail mail, SecurityAudit audit, com.fieldops.identity.application.AccountAttemptLimits limits, com.fieldops.identity.application.UserService users) {
        this.users = users;
        this.limits = limits;
        this.jdbc = jdbc; this.identity = identity; this.context = context; this.tenants = tenants;
        this.memberships = memberships; this.platform = platform; this.mail = mail; this.audit = audit;
    }

    public List<Invitation> inbox() {
        var user = identity.verified();
        return jdbc.query("""
                SELECT i.*, t.name AS business_name FROM invitations i JOIN tenants t ON t.id = i.tenant_id
                WHERE i.email = ? AND i.status = 'PENDING' AND i.expires_at > now()
                ORDER BY i.created_at DESC, i.id LIMIT 100
                """, this::map, user.email());
    }

    public Invitation recipientInvitation(UUID id) {
        var user = identity.verified();
        return jdbc.query("""
                SELECT i.*, t.name AS business_name FROM invitations i JOIN tenants t ON t.id = i.tenant_id
                WHERE i.id = ? AND i.email = ?
                """, this::map, id, user.email()).stream().findFirst().orElseThrow(TenantAccessException::notFound);
    }

    public UUID resolve(String token) {
        // Public discovery discloses only a non-secret reference, never the recipient or business.
        return jdbc.query("SELECT id FROM invitations WHERE token_hash = ? AND status = 'PENDING' AND expires_at > now()",
                (rs, row) -> rs.getObject(1, UUID.class), LinkTokens.hash(token)).stream().findFirst()
                .orElseThrow(() -> new TenantAccessException(400, "INVALID_LINK", "This invitation is unavailable. Sign in to check your invitations."));
    }

    public List<Invitation> listForTenant() {
        var selected = context.require();
        requireOwner(selected.tenantId(), selected.userId());
        return jdbc.query("""
                SELECT i.*, t.name AS business_name FROM invitations i JOIN tenants t ON t.id = i.tenant_id
                WHERE i.tenant_id = ? ORDER BY i.created_at DESC, i.id LIMIT 100
                """, this::map, selected.tenantId());
    }

    @Transactional
    public Invitation invite(String rawEmail, MembershipRole role) {
        var selected = context.require();
        lockTenant(selected.tenantId());
        requireOwner(selected.tenantId(), selected.userId());
        return issue(selected.tenantId(), User.normalizeEmail(rawEmail), role, "TEAM_MEMBER", selected.userId());
    }

    @Transactional
    public Invitation provision(String name, String slug, String rawEmail) {
        var actor = platform.require();
        var tenant = tenants.saveAndFlush(Tenant.provision(name, slug));
        jdbc.update("INSERT INTO business_onboarding(tenant_id, completed_version, version) VALUES (?, 0, 0)", tenant.getId());
        audit.record(actor, tenant.getId(), "BUSINESS_PROVISIONED", "SUCCESS", tenant.getId());
        return issue(tenant.getId(), User.normalizeEmail(rawEmail), MembershipRole.BUSINESS_OWNER, "INITIAL_OWNER", actor);
    }

    @Transactional
    public Invitation reissueOwner(UUID tenant, String rawEmail) {
        var actor = platform.require();
        if (!lockTenant(tenant).equals("PROVISIONING")) throw TenantAccessException.forbidden();
        // Explicit replacement invalidates any previous initial-owner offer, even if addressed elsewhere.
        jdbc.update("""
                UPDATE invitations SET status = 'REVOKED', revoked_at = now(), revoked_by_user_id = ?,
                    updated_at = now(), version = version + 1 WHERE tenant_id = ? AND purpose = 'INITIAL_OWNER' AND status = 'PENDING'
                """, actor, tenant);
        return issue(tenant, User.normalizeEmail(rawEmail), MembershipRole.BUSINESS_OWNER, "INITIAL_OWNER", actor);
    }

    private Invitation issue(UUID tenant, String email, MembershipRole role, String purpose, UUID actor) {
        jdbc.update("""
                UPDATE invitations SET status = 'EXPIRED', updated_at = now(), version = version + 1
                WHERE tenant_id = ? AND email = ? AND status = 'PENDING' AND expires_at <= now()
                """, tenant, email);
        limits.require("invite-actor:" + actor, 30);
        limits.require("invite-recipient:" + email, 5);
        var id = UUID.randomUUID();
        var token = LinkTokens.generate();
        jdbc.update("""
                INSERT INTO invitations(id, tenant_id, email, role, purpose, status, token_hash, invited_by_user_id,
                    created_at, updated_at, expires_at, version)
                VALUES (?, ?, ?, ?, ?, 'PENDING', ?, ?, now(), now(), now() + interval '7 days', 0)
                """, id, tenant, email, role.name(), purpose, LinkTokens.hash(token), actor);
        audit.record(actor, tenant, "INVITATION_CREATED", "SUCCESS", id);
        mail.afterCommit(email, "/invitations/accept", token, id);
        return find(tenant, id);
    }

    @Transactional
    public void revoke(UUID id) {
        var selected = context.require();
        lockTenant(selected.tenantId());
        requireOwner(selected.tenantId(), selected.userId());
        var invitation = find(selected.tenantId(), id);
        if (!invitation.status().equals("PENDING")) return;
        jdbc.update("""
                UPDATE invitations SET status = 'REVOKED', revoked_at = now(), revoked_by_user_id = ?,
                    updated_at = now(), version = version + 1 WHERE id = ? AND tenant_id = ?
                """, selected.userId(), id, selected.tenantId());
        audit.record(selected.userId(), selected.tenantId(), "INVITATION_REVOKED", "SUCCESS", id);
    }

    @Transactional
    public Invitation resend(UUID id) {
        var selected = context.require();
        lockTenant(selected.tenantId());
        requireOwner(selected.tenantId(), selected.userId());
        var invitation = find(selected.tenantId(), id);
        if (invitation.status().equals("ACCEPTED")) throw unavailable();
        if (invitation.status().equals("PENDING")) {
            jdbc.update("""
                    UPDATE invitations SET status = 'REVOKED', revoked_at = now(), revoked_by_user_id = ?,
                        updated_at = now(), version = version + 1 WHERE id = ?
                    """, selected.userId(), id);
        }
        return issue(selected.tenantId(), invitation.email(), MembershipRole.valueOf(invitation.role()), "TEAM_MEMBER", selected.userId());
    }

    @Transactional
    public Acceptance accept(UUID id) {
        var user = identity.verified();
        // Read the recipient-scoped reference first; all mutations then use tenant -> invitation lock order.
        var initial = recipientInvitation(id);
        var tenantState = lockTenant(initial.tenantId());
        var invitation = jdbc.query("""
                SELECT i.*, t.name AS business_name FROM invitations i JOIN tenants t ON t.id = i.tenant_id
                WHERE i.id = ? AND i.email = ? FOR UPDATE OF i
                """, this::map, id, user.email()).stream().findFirst().orElseThrow(TenantAccessException::notFound);
        if (invitation.status().equals("ACCEPTED")) {
            var existing = memberships.findByTenantIdAndUserId(invitation.tenantId(), user.id()).orElseThrow(InvitationService::unavailable);
            if (!tenantState.equals("ACTIVE") || existing.getStatus() != MembershipStatus.ACTIVE) throw unavailable();
            return new Acceptance(invitation.tenantId(), existing.getId());
        }
        if (!invitation.status().equals("PENDING") || !invitation.expiresAt().isAfter(Instant.now())) throw unavailable();
        if (invitation.purpose().equals("INITIAL_OWNER")) {
            if (!tenantState.equals("PROVISIONING") || !platform.lockGrant(invitation.invitedByUserId())) throw unavailable();
        } else {
            if (!tenantState.equals("ACTIVE")) throw unavailable();
            requireOwner(invitation.tenantId(), invitation.invitedByUserId());
        }
        var member = memberships.findByTenantIdAndUserId(invitation.tenantId(), user.id()).orElse(null);
        if (member != null && member.getStatus() != MembershipStatus.ACTIVE) throw unavailable();
        if (member == null) member = memberships.saveAndFlush(Membership.create(invitation.tenantId(), user.id(), MembershipRole.valueOf(invitation.role())));
        // An invitation cannot overwrite an existing role, even when it promises more authority.
        if (jdbc.update("""
                UPDATE invitations SET status = 'ACCEPTED', accepted_at = clock_timestamp(), accepted_by_user_id = ?,
                    updated_at = clock_timestamp(), version = version + 1
                WHERE id = ? AND status = 'PENDING' AND expires_at > clock_timestamp()
                """, user.id(), id) != 1) throw unavailable();
        if (invitation.purpose().equals("INITIAL_OWNER")) {
            if (member.getRole() != MembershipRole.BUSINESS_OWNER) throw unavailable();
            jdbc.update("UPDATE tenants SET status = 'ACTIVE', updated_at = now(), version = version + 1 WHERE id = ?", invitation.tenantId());
        }
        audit.record(user.id(), invitation.tenantId(), "INVITATION_ACCEPTED", "SUCCESS", id);
        return new Acceptance(invitation.tenantId(), member.getId());
    }

    private String lockTenant(UUID tenant) {
        return jdbc.query("SELECT status FROM tenants WHERE id = ? FOR UPDATE", (rs, row) -> rs.getString(1), tenant)
                .stream().findFirst().orElseThrow(TenantAccessException::notFound);
    }
    private void requireOwner(UUID tenant, UUID user) {
        if (!users.isVerifiedActive(user) || !Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM memberships m JOIN tenants t ON t.id = m.tenant_id
                WHERE m.tenant_id = ? AND m.user_id = ? AND m.status = 'ACTIVE' AND m.role = 'BUSINESS_OWNER'
                AND t.status = 'ACTIVE')
                """, Boolean.class, tenant, user))) throw TenantAccessException.forbidden();
    }
    private Invitation find(UUID tenant, UUID id) {
        return jdbc.query("""
                SELECT i.*, t.name AS business_name FROM invitations i JOIN tenants t ON t.id = i.tenant_id
                WHERE i.tenant_id = ? AND i.id = ?
                """, this::map, tenant, id).stream().findFirst().orElseThrow(TenantAccessException::notFound);
    }
    private Invitation map(ResultSet rs, int row) throws SQLException {
        return new Invitation(rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class), rs.getString("business_name"),
                rs.getString("email"), rs.getString("role"), rs.getString("purpose"), rs.getString("status"),
                rs.getTimestamp("expires_at").toInstant(), rs.getObject("invited_by_user_id", UUID.class));
    }
    private static TenantAccessException unavailable() {
        return new TenantAccessException(409, "INVITATION_UNAVAILABLE", "This invitation cannot grant access. Ask the business owner for help.");
    }
    public record Invitation(UUID id, UUID tenantId, String businessName, String email, String role, String purpose,
                             String status, Instant expiresAt, UUID invitedByUserId) { }
    public record Acceptance(UUID tenantId, UUID membershipId) { }
}
