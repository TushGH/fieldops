package com.fieldops.tenant;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.fieldops.FieldOpsApplication;
import com.fieldops.identity.application.PasswordCredentialService;
import com.fieldops.identity.application.UserDetails;
import com.fieldops.identity.application.UserService;
import com.fieldops.tenant.application.MembershipDetails;
import com.fieldops.tenant.application.MembershipService;
import com.fieldops.tenant.application.TenantService;
import com.fieldops.tenant.application.TenantWorkspaceService;
import com.fieldops.tenant.domain.MembershipRole;
import com.fieldops.tenant.domain.Tenant;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.ScopeNotActiveException;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class TenantIsolationIntegrationTest {
    private static final String PASSWORD = "test tenant isolation passphrase";
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.9-alpine");
    private static final UUID LEGACY_TENANT = UUID.randomUUID();
    private static final UUID LEGACY_USER = UUID.randomUUID();
    private static ConfigurableApplicationContext context;
    private static UserService users;
    private static MembershipService memberships;
    private static TenantService tenants;
    private static PasswordCredentialService passwords;
    private static EntityManagerFactory entityManagers;
    private static JdbcTemplate jdbc;
    private static ObjectMapper json;
    private static String baseUrl;
    private static UUID tenantA;
    private static UUID tenantB;
    private static UserDetails ownerA;
    private static UserDetails ownerB;
    private static UserDetails dispatcher;
    private static UserDetails technician;
    private static UserDetails outsider;
    private static MembershipDetails ownerMembershipA;
    private static MembershipDetails ownerMembershipB;
    private static MembershipDetails dispatcherMembership;

    @BeforeAll
    static void start() throws Exception {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .target("4").load().migrate();
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            try (var statement = connection.prepareStatement("""
                    INSERT INTO tenants (id,name,slug,status,created_at,updated_at,version)
                    VALUES (?, 'Legacy', 'legacy', 'ACTIVE', now(), now(), 0)
                    """)) {
                statement.setObject(1, LEGACY_TENANT); statement.executeUpdate();
            }
            try (var statement = connection.prepareStatement("""
                    INSERT INTO users (id,display_name,email,status,created_at,updated_at,version)
                    VALUES (?, 'Legacy', 'legacy@example.com', 'ACTIVE', now(), now(), 0)
                    """)) {
                statement.setObject(1, LEGACY_USER); statement.executeUpdate();
            }
            try (var statement = connection.prepareStatement("""
                    INSERT INTO memberships (id,tenant_id,user_id,status,created_at,updated_at,version)
                    VALUES (?, ?, ?, 'ACTIVE', now(), now(), 0)
                    """)) {
                statement.setObject(1, UUID.randomUUID()); statement.setObject(2, LEGACY_TENANT);
                statement.setObject(3, LEGACY_USER); statement.executeUpdate();
            }
        }
        context = SpringApplication.run(FieldOpsApplication.class, "--server.port=0",
                "--server.servlet.session.cookie.secure=false",
                "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                "--spring.datasource.username=" + POSTGRES.getUsername(),
                "--spring.datasource.password=" + POSTGRES.getPassword());
        users = context.getBean(UserService.class);
        memberships = context.getBean(MembershipService.class);
        tenants = context.getBean(TenantService.class);
        passwords = context.getBean(PasswordCredentialService.class);
        entityManagers = context.getBean(EntityManagerFactory.class);
        jdbc = context.getBean(JdbcTemplate.class);
        json = context.getBean(ObjectMapper.class);
        baseUrl = "http://127.0.0.1:" + context.getEnvironment().getRequiredProperty("local.server.port");
        tenantA = tenants.create("Business A", "business-a").id();
        tenantB = tenants.create("Business B", "business-b").id();
        ownerA = user("owner-a"); ownerB = user("owner-b"); dispatcher = user("dispatcher");
        technician = user("technician"); outsider = user("outsider");
        ownerMembershipA = memberships.create(tenantA, ownerA.id(), MembershipRole.BUSINESS_OWNER);
        ownerMembershipB = memberships.create(tenantB, ownerB.id(), MembershipRole.BUSINESS_OWNER);
        dispatcherMembership = memberships.create(tenantA, dispatcher.id(), MembershipRole.DISPATCHER);
        memberships.create(tenantA, technician.id(), MembershipRole.TECHNICIAN);
        // One identity has different roles in two businesses.
        memberships.create(tenantB, ownerA.id(), MembershipRole.TECHNICIAN);
    }

    @AfterAll
    static void stop() { if (context != null) context.close(); }

    @Test
    void migratesExistingMembershipsWithoutGrantingOwnerPrivileges() {
        assertThat(memberships.find(LEGACY_TENANT, LEGACY_USER).orElseThrow().role()).isEqualTo(MembershipRole.TECHNICIAN);
        assertThat(context.getBean(Flyway.class).info().current().getVersion().getVersion()).isEqualTo("8");
        context.getBean(Flyway.class).validate();
        assertThatThrownBy(() -> jdbc.update("UPDATE memberships SET role = 'PLATFORM_ADMIN' WHERE id = ?", ownerMembershipA.id()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE memberships SET role = NULL WHERE id = ?", ownerMembershipA.id()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void requiresAuthenticationAndExplicitUnambiguousTenantSelection() throws Exception {
        try (var anonymous = new Browser(null)) {
            assertThat(anonymous.get("/api/v1/tenant", tenantA).statusCode()).isEqualTo(401);
        }
        try (var browser = new Browser(ownerA)) {
            assertThat(browser.get("/api/v1/tenant", null).statusCode()).isEqualTo(400);
            assertThat(browser.get("/api/v1/tenant?tenantId=" + tenantA, null).statusCode()).isEqualTo(400);
            assertThat(browser.send("GET", "/api/v1/tenant", "not-a-uuid", null, false).statusCode()).isEqualTo(400);
            assertThat(browser.send("GET", "/api/v1/tenant", tenantA + "," + tenantB, null, false).statusCode()).isEqualTo(400);
            var duplicate = browser.client.send(HttpRequest.newBuilder(URI.create(baseUrl + "/api/v1/tenant"))
                    .header("X-Tenant-ID", tenantA.toString()).header("X-Tenant-ID", tenantB.toString()).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(duplicate.statusCode()).isEqualTo(400);
            assertThat(browser.get("/api/v1/tenant", UUID.randomUUID()).statusCode()).isEqualTo(403);
        }
        try (var browser = new Browser(outsider)) {
            assertThat(browser.get("/api/v1/tenant", tenantA).statusCode()).isEqualTo(403);
            assertThat(browser.get("/api/v1/auth/me", null).statusCode()).isEqualTo(200);
        }
    }

    @Test
    void roleMatrixIsTenantScopedAndCannotBeForgedInHeaders() throws Exception {
        for (var user : new UserDetails[]{dispatcher, technician}) {
            try (var browser = new Browser(user)) {
                assertThat(browser.get("/api/v1/tenant", tenantA).statusCode()).isEqualTo(200);
                assertThat(browser.get("/api/v1/tenant/memberships", tenantA).statusCode()).isEqualTo(403);
                assertThat(browser.write("PATCH", "/api/v1/tenant", tenantA, "{\"name\":\"Forbidden\"}").statusCode()).isEqualTo(403);
                assertThat(browser.write("PUT", "/api/v1/tenant/memberships/" + dispatcherMembership.id() + "/role", tenantA,
                        "{\"role\":\"BUSINESS_OWNER\"}").statusCode()).isEqualTo(403);
                var forged = browser.client.send(HttpRequest.newBuilder(URI.create(baseUrl + "/api/v1/tenant/memberships"))
                        .header("X-Tenant-ID", tenantA.toString()).header("X-Role", "BUSINESS_OWNER")
                        .header("X-User-ID", ownerA.id().toString()).GET().build(), HttpResponse.BodyHandlers.ofString());
                assertThat(forged.statusCode()).isEqualTo(403);
            }
        }
        try (var browser = new Browser(ownerA)) {
            assertThat(browser.get("/api/v1/tenant/memberships", tenantA).statusCode()).isEqualTo(200);
            assertThat(browser.get("/api/v1/tenant/memberships", tenantB).statusCode()).isEqualTo(403);
            assertThat(json.readTree(browser.get("/api/v1/tenant/context", tenantB).body()).path("role").asText()).isEqualTo("TECHNICIAN");
            assertThat(json.readTree(browser.get("/api/v1/tenant/context", tenantA).body()).path("role").asText()).isEqualTo("BUSINESS_OWNER");
        }
    }

    @Test
    void scopesListsCountsPaginationAndResourceReadsToSelectedTenant() throws Exception {
        try (var browser = new Browser(ownerA)) {
            var response = browser.get("/api/v1/tenant/memberships?size=1", tenantA);
            assertThat(response.statusCode()).isEqualTo(200);
            var body = json.readTree(response.body());
            assertThat(body.path("totalElements").asLong()).isEqualTo(3);
            assertThat(body.path("items").size()).isEqualTo(1);
            for (var item : body.path("items")) assertThat(item.path("tenantId").asText()).isEqualTo(tenantA.toString());
            var pageTwo = json.readTree(browser.get("/api/v1/tenant/memberships?size=1&page=1", tenantA).body());
            assertThat(pageTwo.path("items").get(0).path("id").asText())
                    .isNotEqualTo(body.path("items").get(0).path("id").asText());
            assertThat(browser.get("/api/v1/tenant/memberships?size=101", tenantA).statusCode()).isEqualTo(400);
            assertThat(browser.get("/api/v1/tenant/memberships?page=-1", tenantA).statusCode()).isEqualTo(400);
            assertThat(browser.get("/api/v1/tenant/memberships/" + ownerMembershipA.id(), tenantA).statusCode()).isEqualTo(200);
            var foreign = browser.get("/api/v1/tenant/memberships/" + ownerMembershipB.id(), tenantA);
            var missing = browser.get("/api/v1/tenant/memberships/" + UUID.randomUUID(), tenantA);
            assertThat(foreign.statusCode()).isEqualTo(404);
            assertThat(foreign.body()).isEqualTo(missing.body());
        }
    }

    @Test
    void rejectsCrossTenantMutationsAndOwnershipPayloadSpoofingWithoutChangingData() throws Exception {
        var before = memberships.find(tenantB, ownerB.id()).orElseThrow();
        try (var browser = new Browser(ownerA)) {
            var path = "/api/v1/tenant/memberships/" + ownerMembershipB.id();
            assertThat(browser.write("POST", path + "/deactivate", tenantA, "").statusCode()).isEqualTo(404);
            assertThat(browser.write("POST", path + "/reactivate", tenantA, "").statusCode()).isEqualTo(404);
            assertThat(browser.write("PUT", path + "/role", tenantA, "{\"role\":\"TECHNICIAN\"}").statusCode()).isEqualTo(404);
            assertThat(browser.write("PATCH", "/api/v1/tenant", tenantA,
                    "{\"name\":\"Spoof\",\"tenantId\":\"" + tenantB + "\"}").statusCode()).isEqualTo(400);
            assertThat(browser.write("PUT", "/api/v1/tenant/memberships/" + dispatcherMembership.id() + "/role", tenantA,
                    "{\"role\":\"TECHNICIAN\",\"userId\":\"" + ownerB.id() + "\"}").statusCode()).isEqualTo(400);
            assertThat(browser.write("DELETE", path, tenantA, "").statusCode()).isEqualTo(405);
        }
        assertThat(memberships.find(tenantB, ownerB.id())).contains(before);
        assertThat(tenants.findById(tenantB).orElseThrow().name()).isEqualTo("Business B");
    }

    @Test
    void ownerWritesRequireCsrfAndPreserveLastOwner() throws Exception {
        var tenant = tenants.create("Editable", "editable").id();
        var owner = memberships.create(tenant, ownerA.id(), MembershipRole.BUSINESS_OWNER);
        try (var browser = new Browser(ownerA)) {
            assertThat(browser.send("PATCH", "/api/v1/tenant", tenant.toString(), "{\"name\":\"New\"}", false).statusCode()).isEqualTo(403);
            assertThat(browser.write("PATCH", "/api/v1/tenant", tenant, "{\"name\":\"Renamed\"}").statusCode()).isEqualTo(200);
            assertThat(tenants.findById(tenant).orElseThrow().name()).isEqualTo("Renamed");
            assertThat(browser.write("POST", "/api/v1/tenant/memberships/" + owner.id() + "/deactivate", tenant, "").statusCode()).isEqualTo(409);
            assertThat(browser.write("PUT", "/api/v1/tenant/memberships/" + owner.id() + "/role", tenant,
                    "{\"role\":\"DISPATCHER\"}").statusCode()).isEqualTo(409);
            assertThat(browser.write("PUT", "/api/v1/tenant/memberships/" + owner.id() + "/role", tenant,
                    "{\"role\":\"PLATFORM_ADMIN\"}").statusCode()).isEqualTo(400);
            assertThat(browser.write("PUT", "/api/v1/tenant/memberships/" + owner.id() + "/role", tenant,
                    "{\"role\":null}").statusCode()).isEqualTo(400);
        }
    }

    @Test
    void roleRevocationAndMembershipDeactivationApplyToExistingSessions() throws Exception {
        var tenant = tenants.create("Revocation", "revocation").id();
        memberships.create(tenant, ownerA.id(), MembershipRole.BUSINESS_OWNER);
        var target = memberships.create(tenant, ownerB.id(), MembershipRole.BUSINESS_OWNER);
        try (var admin = new Browser(ownerA); var targetBrowser = new Browser(ownerB)) {
            assertThat(targetBrowser.get("/api/v1/tenant/memberships", tenant).statusCode()).isEqualTo(200);
            assertThat(admin.write("PUT", "/api/v1/tenant/memberships/" + target.id() + "/role", tenant,
                    "{\"role\":\"TECHNICIAN\"}").statusCode()).isEqualTo(200);
            assertThat(targetBrowser.get("/api/v1/tenant/memberships", tenant).statusCode()).isEqualTo(403);
            assertThat(targetBrowser.get("/api/v1/tenant", tenant).statusCode()).isEqualTo(200);
            assertThat(admin.write("POST", "/api/v1/tenant/memberships/" + target.id() + "/deactivate", tenant, "").statusCode()).isEqualTo(200);
            assertThat(targetBrowser.get("/api/v1/tenant", tenant).statusCode()).isEqualTo(403);
            assertThat(targetBrowser.get("/api/v1/auth/me", null).statusCode()).isEqualTo(200);
            assertThat(admin.write("POST", "/api/v1/tenant/memberships/" + target.id() + "/reactivate", tenant, "").statusCode()).isEqualTo(200);
            assertThat(targetBrowser.get("/api/v1/tenant", tenant).statusCode()).isEqualTo(200);
        }
    }

    @Test
    void suspendedTenantIsRejectedWithoutEndingGlobalLogin() throws Exception {
        var tenant = tenants.create("Suspended", "suspended").id();
        memberships.create(tenant, ownerA.id(), MembershipRole.BUSINESS_OWNER);
        try (var browser = new Browser(ownerA)) {
            assertThat(browser.get("/api/v1/tenant", tenant).statusCode()).isEqualTo(200);
            try (var em = entityManagers.createEntityManager()) {
                em.getTransaction().begin(); em.find(Tenant.class, tenant).suspend(); em.getTransaction().commit();
            }
            assertThat(browser.get("/api/v1/tenant", tenant).statusCode()).isEqualTo(403);
            assertThat(browser.get("/api/v1/auth/me", null).statusCode()).isEqualTo(200);
        }
    }

    @Test
    void contextDoesNotLeakAcrossConcurrentRequestsOrSurviveMissingHeader() throws Exception {
        try (var browser = new Browser(ownerA); var executor = Executors.newFixedThreadPool(4)) {
            var requests = new java.util.ArrayList<java.util.concurrent.Future<String>>();
            for (int i = 0; i < 12; i++) {
                var tenant = i % 2 == 0 ? tenantA : tenantB;
                requests.add(executor.submit(() -> {
                    var response = browser.get("/api/v1/tenant/context", tenant);
                    assertThat(response.statusCode()).isEqualTo(200);
                    return json.readTree(response.body()).path("tenantId").asText();
                }));
            }
            for (int i = 0; i < requests.size(); i++) {
                assertThat(requests.get(i).get(10, TimeUnit.SECONDS)).isEqualTo((i % 2 == 0 ? tenantA : tenantB).toString());
            }
            assertThat(browser.get("/api/v1/tenant", null).statusCode()).isEqualTo(400);
        }
        assertThatThrownBy(() -> context.getBean(TenantWorkspaceService.class).listMemberships(0, 20))
                .isInstanceOf(ScopeNotActiveException.class);
    }

    @Test
    void concurrentOwnersCannotDeactivateEachOtherAndLeaveNoOwner() throws Exception {
        var tenant = tenants.create("Owner Race", "owner-race").id();
        var first = memberships.create(tenant, ownerA.id(), MembershipRole.BUSINESS_OWNER);
        var second = memberships.create(tenant, ownerB.id(), MembershipRole.BUSINESS_OWNER);
        try (var firstBrowser = new Browser(ownerA); var secondBrowser = new Browser(ownerB);
             var executor = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            var one = executor.submit(() -> { start.await(); return firstBrowser.write("POST", "/api/v1/tenant/memberships/" + second.id() + "/deactivate", tenant, "").statusCode(); });
            var two = executor.submit(() -> { start.await(); return secondBrowser.write("POST", "/api/v1/tenant/memberships/" + first.id() + "/deactivate", tenant, "").statusCode(); });
            start.countDown();
            assertThat(java.util.List.of(one.get(15, TimeUnit.SECONDS), two.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 403);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM memberships WHERE tenant_id = ? AND role = 'BUSINESS_OWNER' AND status = 'ACTIVE'",
                Integer.class, tenant)).isEqualTo(1);
    }

    private static UserDetails user(String label) {
        var user = users.create(label, label + "@example.com");
        jdbc.update("UPDATE users SET email_verified_at = now() WHERE id = ?", user.id());
        passwords.provision(user.id(), PASSWORD);
        return user;
    }

    private static final class Browser implements AutoCloseable {
        private final HttpClient client = HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
        private String csrf;

        Browser(UserDetails user) throws Exception {
            if (user != null) {
                refreshCsrf();
                var request = request("/api/v1/auth/login").header("X-CSRF-TOKEN", csrf)
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString("email=" + URLEncoder.encode(user.email(), StandardCharsets.UTF_8)
                                + "&password=" + URLEncoder.encode(PASSWORD, StandardCharsets.UTF_8))).build();
                assertThat(client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(204);
                refreshCsrf();
            }
        }

        void refreshCsrf() throws Exception {
            csrf = json.readTree(get("/api/v1/auth/csrf", null).body()).path("token").asText();
        }

        HttpResponse<String> get(String path, UUID tenant) throws Exception {
            return send("GET", path, tenant == null ? null : tenant.toString(), null, false);
        }

        HttpResponse<String> write(String method, String path, UUID tenant, String body) throws Exception {
            return send(method, path, tenant.toString(), body, true);
        }

        HttpResponse<String> send(String method, String path, String tenant, String body, boolean includeCsrf) throws Exception {
            var builder = request(path);
            if (tenant != null) builder.header("X-Tenant-ID", tenant);
            if (includeCsrf) builder.header("X-CSRF-TOKEN", csrf);
            if (body != null) builder.header("Content-Type", "application/json");
            builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        }

        private HttpRequest.Builder request(String path) {
            return HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(15));
        }

        @Override
        public void close() { client.close(); }
    }
}
