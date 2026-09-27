package com.fieldops.identity;

import java.sql.DriverManager;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.fieldops.FieldOpsApplication;
import com.fieldops.identity.application.UserService;
import com.fieldops.identity.domain.User;
import com.fieldops.identity.domain.UserStatus;
import com.fieldops.tenant.application.MembershipService;
import com.fieldops.tenant.application.TenantService;
import com.fieldops.tenant.domain.Membership;
import com.fieldops.tenant.domain.MembershipStatus;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.OptimisticLockException;
import jakarta.validation.ConstraintViolationException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class UserMembershipIntegrationTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.9-alpine");
    private static final UUID EXISTING_TENANT = UUID.randomUUID();
    private static ConfigurableApplicationContext context;
    private static UserService users;
    private static TenantService tenants;
    private static MembershipService memberships;
    private static EntityManagerFactory entityManagers;
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void startApplication() throws Exception {
        // Exercise an upgrade with existing V2 tenant data, not just a fresh schema.
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .target("2").load().migrate();
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.prepareStatement("""
                     INSERT INTO tenants (id, name, slug, status, created_at, updated_at, version)
                     VALUES (?, 'Existing Business', 'existing-business', 'ACTIVE', now(), now(), 0)
                     """)) {
            statement.setObject(1, EXISTING_TENANT);
            statement.executeUpdate();
        }
        // Match the existing JUnit 5 setup; Spring 7's extension requires JUnit 6.
        context = SpringApplication.run(FieldOpsApplication.class,
                "--server.port=0", "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                "--spring.datasource.username=" + POSTGRES.getUsername(),
                "--spring.datasource.password=" + POSTGRES.getPassword());
        users = context.getBean(UserService.class);
        tenants = context.getBean(TenantService.class);
        memberships = context.getBean(MembershipService.class);
        entityManagers = context.getBean(EntityManagerFactory.class);
        jdbc = context.getBean(JdbcTemplate.class);
    }

    @AfterAll
    static void stopApplication() {
        if (context != null) context.close();
    }

    @Test
    void upgradesV2AndSupportsUsersWithZeroOneOrMultipleTenants() {
        var flyway = context.getBean(Flyway.class);
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("4");
        flyway.validate();
        assertThat(tenants.findById(EXISTING_TENANT)).get().extracting(t -> t.name()).isEqualTo("Existing Business");
        var user = users.create(" Alex ", " Alex+Work@Example.COM ");
        assertThat(users.findById(user.id())).contains(user);
        assertThat(user.email()).isEqualTo("alex+work@example.com");
        assertThat(user.version()).isZero();
        assertThat(user.createdAt()).isEqualTo(user.updatedAt());
        assertThat(users.findById(UUID.randomUUID())).isEmpty();
        assertThat(memberships.find(EXISTING_TENANT, user.id())).isEmpty();
        var otherTenant = tenants.create("Another Business", "another-business");
        var first = memberships.create(EXISTING_TENANT, user.id());
        var second = memberships.create(otherTenant.id(), user.id());
        assertThat(first.id()).isNotEqualTo(second.id());
        assertThat(first.version()).isZero();
        assertThat(first.createdAt()).isEqualTo(first.updatedAt());
        assertThat(memberships.find(EXISTING_TENANT, user.id())).contains(first);
        assertThat(memberships.find(otherTenant.id(), user.id())).contains(second);
        assertThat(memberships.find(UUID.randomUUID(), user.id())).isEmpty();
        var colleague = users.create("Alex", "colleague@example.com");
        memberships.create(EXISTING_TENANT, colleague.id());
        assertThat(users.findById(colleague.id())).contains(colleague);
    }

    @Test
    void globallyReservesCanonicalEmailsEvenForDisabledUsers() {
        var user = users.create("Original", "unique@example.com");
        try (var em = entityManagers.createEntityManager()) {
            em.getTransaction().begin();
            em.find(User.class, user.id()).disable();
            em.getTransaction().commit();
        }
        assertThatThrownBy(() -> users.create("Another Person", " UNIQUE@EXAMPLE.COM "))
                .isInstanceOf(DataIntegrityViolationException.class).hasStackTraceContaining("uq_users_email");
        assertThatThrownBy(() -> users.create("Invalid", "not-an-email"))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void membershipLifecycleIsIndependentAcrossTenantsAndKeepsPairReserved() {
        var user = users.create("Member", "member@example.com");
        var tenant = tenants.create("Second Membership Business", "membership-business");
        var first = memberships.create(EXISTING_TENANT, user.id());
        var second = memberships.create(tenant.id(), user.id());
        try (var em = entityManagers.createEntityManager()) {
            em.getTransaction().begin();
            em.find(Membership.class, first.id()).deactivate();
            em.getTransaction().commit();
        }
        var inactive = memberships.find(EXISTING_TENANT, user.id()).orElseThrow();
        assertThat(inactive.status()).isEqualTo(MembershipStatus.INACTIVE);
        assertThat(inactive.version()).isEqualTo(1);
        assertThat(inactive.createdAt()).isEqualTo(first.createdAt());
        assertThat(inactive.updatedAt()).isAfter(first.updatedAt());
        assertThat(memberships.find(tenant.id(), user.id())).contains(second);
        assertThat(users.findById(user.id())).contains(user);
        assertThatThrownBy(() -> memberships.create(EXISTING_TENANT, user.id()))
                .isInstanceOf(DataIntegrityViolationException.class).hasStackTraceContaining("uq_memberships_tenant_user");
        try (var em = entityManagers.createEntityManager()) {
            em.getTransaction().begin();
            em.find(Membership.class, first.id()).reactivate();
            em.getTransaction().commit();
        }
        var active = memberships.find(EXISTING_TENANT, user.id()).orElseThrow();
        assertThat(active.id()).isEqualTo(first.id());
        assertThat(active.status()).isEqualTo(MembershipStatus.ACTIVE);
        assertThat(active.version()).isEqualTo(2);
    }

    @Test
    void rejectsMissingReferencesAndRestrictsDeletingReferencedRecords() {
        var user = users.create("Referenced User", "referenced@example.com");
        assertThatThrownBy(() -> memberships.create(UUID.randomUUID(), user.id()))
                .isInstanceOf(DataIntegrityViolationException.class).hasStackTraceContaining("fk_memberships_tenant");
        assertThatThrownBy(() -> memberships.create(EXISTING_TENANT, UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class).hasStackTraceContaining("fk_memberships_user");
        memberships.create(EXISTING_TENANT, user.id());
        assertThatThrownBy(() -> jdbc.update("DELETE FROM users WHERE id = ?", user.id()))
                .isInstanceOf(DataIntegrityViolationException.class).hasStackTraceContaining("fk_memberships_user");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM tenants WHERE id = ?", EXISTING_TENANT))
                .isInstanceOf(DataIntegrityViolationException.class).hasStackTraceContaining("fk_memberships_tenant");
    }

    @Test
    void rejectsStaleUserAndMembershipUpdates() {
        var user = users.create("Current Name", "stale@example.com");
        var membership = memberships.create(EXISTING_TENANT, user.id());
        try (var first = entityManagers.createEntityManager(); var second = entityManagers.createEntityManager()) {
            first.getTransaction().begin();
            second.getTransaction().begin();
            var current = first.find(User.class, user.id());
            var stale = second.find(User.class, user.id());
            current.disable();
            first.getTransaction().commit();
            stale.rename("Lost Update");
            assertThatThrownBy(second::flush).isInstanceOf(OptimisticLockException.class);
            second.getTransaction().rollback();
        }
        var disabled = users.findById(user.id()).orElseThrow();
        assertThat(disabled.status()).isEqualTo(UserStatus.DISABLED);
        assertThat(disabled.displayName()).isEqualTo(user.displayName());
        assertThat(disabled.version()).isEqualTo(1);
        assertThat(disabled.createdAt()).isEqualTo(user.createdAt());
        assertThat(disabled.updatedAt()).isAfter(user.updatedAt());
        // Disabling a global user does not rewrite or erase membership history.
        assertThat(memberships.find(EXISTING_TENANT, user.id())).contains(membership);
        try (var first = entityManagers.createEntityManager(); var second = entityManagers.createEntityManager()) {
            first.getTransaction().begin();
            var stale = first.find(Membership.class, membership.id());
            second.getTransaction().begin();
            second.find(Membership.class, membership.id()).deactivate();
            second.getTransaction().commit();
            stale.deactivate();
            assertThatThrownBy(first::flush).isInstanceOf(OptimisticLockException.class);
            first.getTransaction().rollback();
        }
    }

    @Test
    void databaseRejectsInvalidDirectWrites() {
        var user = users.create("Constraint User", "constraints@example.com");
        var membership = memberships.create(EXISTING_TENANT, user.id());
        for (var column : new String[]{"id", "display_name", "email", "status", "created_at", "updated_at", "version"}) {
            assertThatThrownBy(() -> jdbc.update("UPDATE users SET " + column + " = NULL WHERE id = ?", user.id()))
                    .as("User NOT NULL: %s", column).isInstanceOf(DataIntegrityViolationException.class);
        }
        for (var column : new String[]{"id", "tenant_id", "user_id", "status", "created_at", "updated_at", "version"}) {
            assertThatThrownBy(() -> jdbc.update("UPDATE memberships SET " + column + " = NULL WHERE id = ?", membership.id()))
                    .as("Membership NOT NULL: %s", column).isInstanceOf(DataIntegrityViolationException.class);
        }
        for (var name : new String[]{"", " ", "\u00a0Name", "Name\u2003", "Bad\nName", "x".repeat(201)}) {
            assertThatThrownBy(() -> jdbc.update("UPDATE users SET display_name = ? WHERE id = ?", name, user.id()))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
        for (var email : new String[]{"", "not-an-email", "Upper@example.com", " a@b.com", "a@b.com ", "a@@b.com", "é@b.com", "a".repeat(250) + "@b.com"}) {
            assertThatThrownBy(() -> jdbc.update("UPDATE users SET email = ? WHERE id = ?", email, user.id()))
                    .as("Invalid email: %s", email).isInstanceOf(DataIntegrityViolationException.class);
        }
        assertThatThrownBy(() -> jdbc.update("UPDATE users SET status = 'PENDING' WHERE id = ?", user.id()))
                .isInstanceOf(DataIntegrityViolationException.class).hasStackTraceContaining("ck_users_status");
        assertThatThrownBy(() -> jdbc.update("UPDATE memberships SET status = 'INVITED' WHERE id = ?", membership.id()))
                .isInstanceOf(DataIntegrityViolationException.class).hasStackTraceContaining("ck_memberships_status");
        assertThatThrownBy(() -> jdbc.update("UPDATE users SET version = -1 WHERE id = ?", user.id()))
                .isInstanceOf(DataIntegrityViolationException.class).hasStackTraceContaining("ck_users_version");
        assertThatThrownBy(() -> jdbc.update("UPDATE memberships SET version = -1 WHERE id = ?", membership.id()))
                .isInstanceOf(DataIntegrityViolationException.class).hasStackTraceContaining("ck_memberships_version");
        assertThat(users.findById(user.id())).contains(user);
        assertThat(memberships.find(EXISTING_TENANT, user.id())).contains(membership);
    }

    @Test
    void failedMembershipCreationRollsBackAnEnclosingUserCreation() {
        var transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            var user = users.create("Rollback User", "rollback@example.com");
            memberships.create(UUID.randomUUID(), user.id());
        })).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE email = 'rollback@example.com'", Integer.class))
                .isZero();
    }

    @Test
    void concurrentEmailAndMembershipClaimsHaveOneWinner() throws Exception {
        assertOneWinner(() -> users.create("Race User", "race@example.com"), "uq_users_email");
        var user = users.create("Member Race", "member-race@example.com");
        assertOneWinner(() -> memberships.create(EXISTING_TENANT, user.id()), "uq_memberships_tenant_user");
    }

    private static void assertOneWinner(Runnable action, String constraint) throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        Callable<Boolean> claim = () -> {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start timed out");
            try {
                action.run();
                return true;
            } catch (DataIntegrityViolationException exception) {
                assertThat(exception).hasStackTraceContaining(constraint);
                return false;
            }
        };
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(claim);
            var second = executor.submit(claim);
            try {
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            } finally {
                start.countDown();
            }
            assertThat(first.get(20, TimeUnit.SECONDS) ^ second.get(20, TimeUnit.SECONDS)).isTrue();
        }
    }
}
