package com.fieldops.tenant;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.fieldops.FieldOpsApplication;
import com.fieldops.tenant.application.TenantService;
import com.fieldops.tenant.domain.Tenant;
import com.fieldops.tenant.domain.TenantStatus;
import com.fieldops.tenant.infrastructure.TenantRepository;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.OptimisticLockException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class TenantPersistenceIntegrationTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.9-alpine");

    private static ConfigurableApplicationContext context;
    private static TenantService service;
    private static TenantRepository repository;
    private static JdbcTemplate jdbc;
    private static EntityManagerFactory entityManagers;

    @BeforeAll
    static void startApplication() {
        // Keep the project's JUnit 5 lifecycle rather than Spring 7's JUnit 6 extension.
        context = SpringApplication.run(FieldOpsApplication.class,
                "--server.port=0",
                "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                "--spring.datasource.username=" + POSTGRES.getUsername(),
                "--spring.datasource.password=" + POSTGRES.getPassword());
        service = context.getBean(TenantService.class);
        repository = context.getBean(TenantRepository.class);
        jdbc = context.getBean(JdbcTemplate.class);
        entityManagers = context.getBean(EntityManagerFactory.class);
    }

    @AfterAll
    static void stopApplication() {
        if (context != null) context.close();
    }

    @Test
    void migratesAndRoundTripsTenantThroughSeparateTransactions() {
        var created = service.create(" ABC Heating ", " ABC-Heating ");
        assertThat(created.id().version()).isEqualTo(4);
        assertThat(created.name()).isEqualTo("ABC Heating");
        assertThat(created.slug()).isEqualTo("abc-heating");
        assertThat(created.status()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(created.version()).isZero();
        assertThat(created.updatedAt()).isEqualTo(created.createdAt());
        assertThat(service.findById(created.id())).contains(created);
        assertThat(repository.findBySlug("abc-heating")).get()
                .extracting(Tenant::getId).isEqualTo(created.id());
        assertThat(service.findById(UUID.randomUUID())).isEmpty();
        assertThat(repository.findBySlug("missing-slug")).isEmpty();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version IN ('1', '2') AND success",
                Integer.class)).isEqualTo(2);
        context.getBean(Flyway.class).validate();
    }

    @Test
    void allowsDuplicateNamesButReservesSlugsAcrossStatuses() {
        var created = service.create("Shared Name", "shared-name-one");
        service.create("Shared Name", "shared-name-two");
        try (var em = entityManagers.createEntityManager()) {
            em.getTransaction().begin();
            em.find(Tenant.class, created.id()).suspend();
            em.getTransaction().commit();
        }
        assertThatThrownBy(() -> service.create("Other Name", " SHARED-NAME-ONE "))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasStackTraceContaining("uq_tenants_slug");
        assertThat(service.findById(created.id())).get()
                .extracting(details -> details.status()).isEqualTo(TenantStatus.SUSPENDED);
    }

    @Test
    void persistsChangesAndRejectsStaleUpdates() {
        var created = service.create("Original Name", "concurrent-update");
        try (var first = entityManagers.createEntityManager();
             var second = entityManagers.createEntityManager()) {
            first.getTransaction().begin();
            second.getTransaction().begin();
            var current = first.find(Tenant.class, created.id());
            var stale = second.find(Tenant.class, created.id());
            current.suspend();
            first.getTransaction().commit();
            stale.rename("Stale Name");
            assertThatThrownBy(second::flush).isInstanceOf(OptimisticLockException.class);
            second.getTransaction().rollback();
        }
        var suspended = service.findById(created.id()).orElseThrow();
        assertThat(suspended.status()).isEqualTo(TenantStatus.SUSPENDED);
        assertThat(suspended.name()).isEqualTo("Original Name");
        assertThat(suspended.createdAt()).isEqualTo(created.createdAt());
        assertThat(suspended.updatedAt()).isAfter(created.updatedAt());
        assertThat(suspended.version()).isEqualTo(1);
        try (var em = entityManagers.createEntityManager()) {
            em.getTransaction().begin();
            var tenant = em.find(Tenant.class, created.id());
            tenant.reactivate();
            tenant.rename("Renamed Business");
            em.getTransaction().commit();
        }
        var renamed = service.findById(created.id()).orElseThrow();
        assertThat(renamed.name()).isEqualTo("Renamed Business");
        assertThat(renamed.status()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(renamed.slug()).isEqualTo(created.slug());
        assertThat(renamed.version()).isEqualTo(2);
        try (var em = entityManagers.createEntityManager()) {
            em.getTransaction().begin();
            em.find(Tenant.class, created.id()).reactivate();
            em.getTransaction().commit();
        }
        assertThat(service.findById(created.id())).contains(renamed);
    }

    @Test
    void concurrentSlugClaimsHaveExactlyOneWinner() throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        Callable<Boolean> claim = () -> {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start timed out");
            try {
                service.create("Concurrent Business", "concurrent-slug");
                return true;
            } catch (DataIntegrityViolationException exception) {
                assertThat(exception).hasStackTraceContaining("uq_tenants_slug");
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
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tenants WHERE slug = 'concurrent-slug'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void databaseRejectsInvalidValuesEvenWhenDomainValidationIsBypassed() {
        var created = service.create("Constraint Test", "constraint-test");
        for (var column : new String[]{"id", "name", "slug", "status", "created_at", "updated_at", "version"}) {
            assertThatThrownBy(() -> jdbc.update("UPDATE tenants SET " + column + " = NULL WHERE id = ?", created.id()))
                    .as("NOT NULL: %s", column).isInstanceOf(DataIntegrityViolationException.class);
        }
        for (var invalid : new String[]{"", " ", " Leading", "Trailing ", "\tLeading", "Trailing\n", "Bad\tName", "\u00a0Leading", "Trailing\u2003", "x".repeat(201)}) {
            assertThatThrownBy(() -> jdbc.update("UPDATE tenants SET name = ? WHERE id = ?", invalid, created.id()))
                    .as("Invalid name").isInstanceOf(DataIntegrityViolationException.class);
        }
        for (var invalid : new String[]{"", "ab", "UPPER", "-abc", "abc-", "a--bc", "a_bc", "a bc", "café", "a".repeat(64)}) {
            assertThatThrownBy(() -> jdbc.update("UPDATE tenants SET slug = ? WHERE id = ?", invalid, created.id()))
                    .as("Invalid slug: %s", invalid).isInstanceOf(DataIntegrityViolationException.class);
        }
        assertThatThrownBy(() -> jdbc.update("UPDATE tenants SET status = 'PENDING' WHERE id = ?", created.id()))
                .isInstanceOf(DataIntegrityViolationException.class).hasStackTraceContaining("ck_tenants_status");
        assertThatThrownBy(() -> jdbc.update("UPDATE tenants SET version = -1 WHERE id = ?", created.id()))
                .isInstanceOf(DataIntegrityViolationException.class).hasStackTraceContaining("ck_tenants_version");
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO tenants (id, name, slug, status, created_at, updated_at, version)
                VALUES (?, 'Duplicate ID', 'duplicate-id', 'ACTIVE', ?, ?, 0)
                """, created.id(), Timestamp.from(Instant.now()), Timestamp.from(Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class).hasStackTraceContaining("pk_tenants");
        assertThat(service.findById(created.id())).contains(created);
    }
}
