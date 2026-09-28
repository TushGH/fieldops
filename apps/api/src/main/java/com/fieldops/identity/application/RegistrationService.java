package com.fieldops.identity.application;

import java.util.UUID;
import com.fieldops.audit.SecurityAudit;
import com.fieldops.identity.domain.User;
import com.fieldops.identity.infrastructure.LinkTokens;
import com.fieldops.notification.AccountMail;
import com.fieldops.tenant.application.TenantAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {
    private final AccountAttemptLimits limits;
    private final JdbcTemplate jdbc;
    private final UserService users;
    private final PasswordCredentialService credentials;
    private final CurrentIdentity identity;
    private final AccountMail mail;
    private final SecurityAudit audit;

    public RegistrationService(JdbcTemplate jdbc, UserService users, PasswordCredentialService credentials,
                               CurrentIdentity identity, AccountMail mail, SecurityAudit audit, AccountAttemptLimits limits) {
        this.limits = limits;
        this.jdbc = jdbc; this.users = users; this.credentials = credentials;
        this.identity = identity; this.mail = mail; this.audit = audit;
    }

    @Transactional
    public void requestRegistration(String displayName, String rawEmail) {
        var candidate = User.create(displayName, rawEmail);
        var email = candidate.getEmail();
        limits.require("signup:" + email, 5);
        // Existing identities are never replaced or assigned a new password through registration.
        if (jdbc.queryForObject("SELECT count(*) FROM users WHERE email = ?", Integer.class, email) > 0) return;
        issue(email, candidate.getDisplayName(), null, "REGISTRATION", "/signup/complete");
    }

    @Transactional
    public void requestVerification() {
        var user = identity.require();
        limits.require("verify:" + user.id(), 5);
        if (user.emailVerifiedAt() == null) issue(user.email(), null, user.id(), "EMAIL_VERIFICATION", "/verify-email");
    }

    private void issue(String email, String name, UUID user, String purpose, String path) {
        var id = UUID.randomUUID();
        var token = LinkTokens.generate();
        jdbc.update("""
                INSERT INTO email_challenges(id, purpose, email, display_name, user_id, token_hash, created_at, expires_at, version)
                VALUES (?, ?, ?, ?, ?, ?, now(), now() + interval '24 hours', 0)
                """, id, purpose, email, name, user, LinkTokens.hash(token));
        audit.record(user, null, "EMAIL_CHALLENGE_CREATED", "SUCCESS", id);
        mail.afterCommit(email, path, token, id);
    }

    @Transactional
    public void completeRegistration(String token, String password) {
        var challenge = lock(token, "REGISTRATION");
        var user = users.create(challenge.name(), challenge.email());
        credentials.provision(user.id(), password);
        jdbc.update("UPDATE users SET email_verified_at = now(), updated_at = now(), version = version + 1 WHERE id = ?", user.id());
        consume(challenge.id());
        audit.record(user.id(), null, "REGISTRATION_COMPLETED", "SUCCESS", user.id());
    }

    @Transactional
    public void confirmVerification(String token) {
        var user = identity.require();
        var challenge = lock(token, "EMAIL_VERIFICATION");
        if (!user.id().equals(challenge.user()) || !user.email().equals(challenge.email())) throw invalidLink();
        jdbc.update("UPDATE users SET email_verified_at = coalesce(email_verified_at, now()), updated_at = now(), version = version + 1 WHERE id = ?", user.id());
        consume(challenge.id());
        audit.record(user.id(), null, "EMAIL_VERIFIED", "SUCCESS", user.id());
    }

    private Challenge lock(String token, String purpose) {
        return jdbc.query("""
                SELECT id, email, display_name, user_id FROM email_challenges
                WHERE token_hash = ? AND purpose = ? AND consumed_at IS NULL AND revoked_at IS NULL
                  AND expires_at > clock_timestamp() FOR UPDATE
                """, (rs, row) -> new Challenge(rs.getObject("id", UUID.class), rs.getString("email"),
                rs.getString("display_name"), rs.getObject("user_id", UUID.class)), LinkTokens.hash(token), purpose)
                .stream().findFirst().orElseThrow(RegistrationService::invalidLink);
    }

    private void consume(UUID id) {
        if (jdbc.update("""
                UPDATE email_challenges SET consumed_at = clock_timestamp(), version = version + 1
                WHERE id = ? AND consumed_at IS NULL AND revoked_at IS NULL AND expires_at > clock_timestamp()
                """, id) != 1) throw invalidLink();
    }
    private static TenantAccessException invalidLink() {
        return new TenantAccessException(400, "INVALID_LINK", "This link is invalid, expired, or already used. Request another email.");
    }
    private record Challenge(UUID id, String email, String name, UUID user) { }
}
