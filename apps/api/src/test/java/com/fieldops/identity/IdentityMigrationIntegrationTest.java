package com.fieldops.identity;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class IdentityMigrationIntegrationTest {
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.9-alpine");
    @Test void upgradesV5WithoutInventingVerificationOrLosingExistingIdentityAndRelationships() throws Exception {
        var source = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var jdbc = new JdbcTemplate(source);
        Flyway.configure().dataSource(source).target("5").load().migrate();
        var user = UUID.randomUUID(); var tenant = UUID.randomUUID(); var member = UUID.randomUUID();
        jdbc.update("INSERT INTO users VALUES (?, 'Legacy', 'legacy@example.com', 'ACTIVE', now(), now(), 3)", user);
        jdbc.update("INSERT INTO tenants VALUES (?, 'Legacy business', 'legacy-business', 'SUSPENDED', now(), now(), 4)", tenant);
        jdbc.update("INSERT INTO memberships VALUES (?, ?, ?, 'INACTIVE', now(), now(), 5, 'DISPATCHER')", member, tenant, user);
        jdbc.update("INSERT INTO password_credentials VALUES (?, ?, now(), 6)", user, "{bcrypt}$2a$12$" + "a".repeat(53));
        var tenantBefore = jdbc.queryForMap("SELECT * FROM tenants WHERE id = ?", tenant);
        var memberBefore = jdbc.queryForMap("SELECT * FROM memberships WHERE id = ?", member);
        var credentialBefore = jdbc.queryForMap("SELECT * FROM password_credentials WHERE user_id = ?", user);
        var userBefore = jdbc.queryForMap("SELECT * FROM users WHERE id = ?", user);
        var flyway = Flyway.configure().dataSource(source).load();
        flyway.migrate(); flyway.validate();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("8");
        assertThat(jdbc.queryForMap("SELECT * FROM tenants WHERE id = ?", tenant)).isEqualTo(tenantBefore);
        assertThat(jdbc.queryForMap("SELECT * FROM memberships WHERE id = ?", member)).isEqualTo(memberBefore);
        assertThat(jdbc.queryForMap("SELECT * FROM password_credentials WHERE user_id = ?", user)).isEqualTo(credentialBefore);
        var userAfter = jdbc.queryForMap("SELECT * FROM users WHERE id = ?", user);
        assertThat(userAfter.remove("email_verified_at")).isNull(); assertThat(userAfter).isEqualTo(userBefore);
        assertThat(jdbc.queryForObject("SELECT completed_version FROM business_onboarding WHERE tenant_id = ?", Integer.class, tenant)).isZero();
        assertThat(jdbc.queryForObject("SELECT completed_at FROM business_onboarding WHERE tenant_id = ?", Object.class, tenant)).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM platform_role_grants", Integer.class)).isZero();
        jdbc.update("UPDATE tenants SET status = 'PROVISIONING' WHERE id = ?", tenant);
        assertThatThrownBy(() -> jdbc.update("UPDATE business_onboarding SET completed_version = 1 WHERE tenant_id = ?", tenant)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO membership_onboarding VALUES (?, 'TECHNICIAN_SETUP', 1, NULL, 0)", member)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        var invite = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO invitations(id, tenant_id, email, role, purpose, status, token_hash, invited_by_user_id, created_at, updated_at, expires_at, version)
                VALUES (?, ?, 'invite@example.com', 'BUSINESS_OWNER', 'INITIAL_OWNER', 'PENDING', ?, ?, now(), now(), now() + interval '1 day', 0)
                """, invite, tenant, "a".repeat(64), user);
        assertThatThrownBy(() -> jdbc.update("UPDATE invitations SET status = 'ACCEPTED' WHERE id = ?", invite)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE invitations SET status = 'REVOKED', revoked_at = now() WHERE id = ?", invite)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE invitations SET email = 'UPPER@example.com' WHERE id = ?", invite)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO invitations SELECT ?, tenant_id, email, role, purpose, status, ?, invited_by_user_id,
                created_at, updated_at, expires_at, accepted_at, accepted_by_user_id, revoked_at, revoked_by_user_id, version FROM invitations WHERE id = ?
                """, UUID.randomUUID(), "b".repeat(64), invite)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        // Fresh Flyway invocation has no pending migrations and preserves the schema checksums.
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }
}
