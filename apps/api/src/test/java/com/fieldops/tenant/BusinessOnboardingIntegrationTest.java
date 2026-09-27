package com.fieldops.tenant;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import com.fieldops.FieldOpsApplication;
import com.fieldops.identity.application.UserService;
import com.fieldops.tenant.application.MembershipService;
import com.fieldops.tenant.application.TenantService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class BusinessOnboardingIntegrationTest {
    private static final String PASSWORD = "test onboarding passphrase"; // Test-only, never seeded.
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.9-alpine");
    private static ConfigurableApplicationContext context;
    private static JdbcTemplate jdbc;
    private static ObjectMapper json;
    private static String baseUrl;

    @BeforeAll
    static void start() {
        context = SpringApplication.run(FieldOpsApplication.class, "--server.port=0",
                "--server.servlet.session.cookie.secure=false",
                "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                "--spring.datasource.username=" + POSTGRES.getUsername(),
                "--spring.datasource.password=" + POSTGRES.getPassword());
        jdbc = context.getBean(JdbcTemplate.class);
        json = context.getBean(ObjectMapper.class);
        baseUrl = "http://127.0.0.1:" + context.getEnvironment().getRequiredProperty("local.server.port");
    }

    @AfterAll
    static void stop() { if (context != null) context.close(); }

    @Test
    void newBusinessOnboardsAndItsOwnerCanLoginAndAccessOnlyTheirBusiness() throws Exception {
        var input = input();
        input.put("businessName", "  Oak Plumbing  ");
        input.put("email", " OWNER-" + UUID.randomUUID() + "@EXAMPLE.COM ");
        try (var browser = new Browser()) {
            var response = browser.onboard(input, true);
            assertThat(response.statusCode()).isEqualTo(201);
            assertThat(response.body()).isEmpty();
            assertThat(response.headers().firstValue("cache-control")).hasValue("no-store");
            assertThat(browser.get("/api/v1/auth/me", null).statusCode()).isEqualTo(401);
            assertThat(browser.login(input.get("email"), PASSWORD).statusCode()).isEqualTo(204);
            var me = json.readTree(browser.get("/api/v1/auth/me", null).body());
            assertThat(me.path("email").asText()).isEqualTo(input.get("email").strip().toLowerCase());
            var businesses = browser.get("/api/v1/businesses", null);
            assertThat(businesses.statusCode()).isEqualTo(200);
            assertThat(businesses.headers().firstValue("cache-control")).hasValue("no-store");
            var list = json.readTree(businesses.body());
            assertThat(list.size()).isEqualTo(1);
            var business = list.get(0);
            assertThat(business.path("name").asText()).isEqualTo("Oak Plumbing");
            assertThat(business.path("role").asText()).isEqualTo("BUSINESS_OWNER");
            var tenantId = business.path("id").asText();
            var selection = browser.get("/api/v1/tenant/context", tenantId);
            assertThat(selection.statusCode()).isEqualTo(200);
            assertThat(json.readTree(selection.body()).path("userId").asText()).isEqualTo(me.path("id").asText());
            assertThat(browser.get("/api/v1/tenant", UUID.randomUUID().toString()).statusCode()).isEqualTo(403);
            var hash = jdbc.queryForObject("select password_hash from password_credentials where user_id = ?", String.class,
                    UUID.fromString(me.path("id").asText()));
            assertThat(context.getBean(PasswordEncoder.class).matches(PASSWORD, hash)).isTrue();
            assertThat(businesses.body()).doesNotContain("password", "bcrypt", "email");
            context.getBean(Flyway.class).validate();
        }
    }

    @Test
    void duplicateSlugAndCanonicalEmailRollbackAllRecordsAndNeverLinkAnExistingUser() throws Exception {
        var original = input();
        try (var browser = new Browser()) {
            assertThat(browser.onboard(original, true).statusCode()).isEqualTo(201);
            var before = counts();
            var duplicateSlug = input();
            duplicateSlug.put("slug", original.get("slug"));
            var slugFailure = browser.onboard(duplicateSlug, true);
            assertThat(slugFailure.statusCode()).isEqualTo(409);
            assertThat(counts()).isEqualTo(before);
            var duplicateEmail = input();
            duplicateEmail.put("email", " " + original.get("email").toUpperCase() + " ");
            var emailFailure = browser.onboard(duplicateEmail, true);
            assertThat(emailFailure.statusCode()).isEqualTo(409);
            assertThat(emailFailure.body()).isEqualTo(slugFailure.body()).doesNotContain(original.get("email"), "constraint", "SQL");
            assertThat(counts()).isEqualTo(before);
            assertThat(browser.login(original.get("email"), PASSWORD).statusCode()).isEqualTo(204);
            assertThat(json.readTree(browser.get("/api/v1/businesses", null).body()).size()).isEqualTo(1);
        }
    }

    @Test
    void invalidInputAndForgedOwnershipAreRejectedWithoutPartialSetup() throws Exception {
        var invalid = Map.of("password", "short", "email", "invalid", "slug", "bad--slug", "businessName", " ",
                "ownerName", "\u0001", "tenantId", UUID.randomUUID().toString(), "role", "BUSINESS_OWNER", "userId", UUID.randomUUID().toString());
        try (var browser = new Browser()) {
            var before = counts();
            for (var field : invalid.entrySet()) {
                var request = input();
                request.put(field.getKey(), field.getValue());
                assertThat(browser.onboard(request, true).statusCode()).as(field.getKey()).isEqualTo(400);
                assertThat(counts()).isEqualTo(before);
            }
            var oversized = input();
            oversized.put("password", "é".repeat(37));
            assertThat(browser.onboard(oversized, true).statusCode()).isEqualTo(400);
            assertThat(counts()).isEqualTo(before);
        }
    }

    @Test
    void signupRequiresCsrfAndBusinessDiscoveryRequiresAuthentication() throws Exception {
        try (var browser = new Browser()) {
            var before = counts();
            assertThat(browser.onboard(input(), false).statusCode()).isEqualTo(403);
            assertThat(browser.get("/api/v1/businesses", null).statusCode()).isEqualTo(401);
            assertThat(counts()).isEqualTo(before);
        }
    }

    @Test
    void concurrentDuplicateSubmissionsCreateExactlyOneCompleteBusiness() throws Exception {
        var request = input();
        var before = counts();
        Callable<Integer> signup = () -> {
            try (var browser = new Browser()) { return browser.onboard(request, true).statusCode(); }
        };
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var a = executor.submit(signup);
            var b = executor.submit(signup);
            assertThat(java.util.List.of(a.get(), b.get())).containsExactlyInAnyOrder(201, 409);
        }
        var after = counts();
        before.forEach((table, count) -> assertThat(after.get(table)).isEqualTo(count + 1));
    }

    @Test
    void discoveryUsesSessionIdentityAndExcludesInactiveAndForeignBusinesses() throws Exception {
        var input = input();
        try (var browser = new Browser()) {
            assertThat(browser.onboard(input, true).statusCode()).isEqualTo(201);
            assertThat(browser.login(input.get("email"), PASSWORD).statusCode()).isEqualTo(204);
            var userId = UUID.fromString(json.readTree(browser.get("/api/v1/auth/me", null).body()).path("id").asText());
            var tenants = context.getBean(TenantService.class);
            var memberships = context.getBean(MembershipService.class);
            var second = tenants.create("Second business", "second-" + UUID.randomUUID());
            memberships.create(second.id(), userId);
            var suspended = tenants.create("Suspended", "suspended-" + UUID.randomUUID());
            memberships.create(suspended.id(), userId);
            jdbc.update("update tenants set status = 'SUSPENDED' where id = ?", suspended.id());
            var inactive = tenants.create("Inactive membership", "inactive-" + UUID.randomUUID());
            memberships.create(inactive.id(), userId);
            jdbc.update("update memberships set status = 'INACTIVE' where tenant_id = ?", inactive.id());
            var other = context.getBean(UserService.class).create("Other", UUID.randomUUID() + "@example.com");
            var foreign = tenants.create("Foreign", "foreign-" + UUID.randomUUID());
            memberships.create(foreign.id(), other.id());
            var response = browser.get("/api/v1/businesses?userId=" + other.id(), foreign.id().toString());
            assertThat(json.readTree(response.body()).size()).isEqualTo(2);
            assertThat(response.body()).contains(second.id().toString()).doesNotContain(
                    suspended.id().toString(), inactive.id().toString(), foreign.id().toString());
            assertThat(browser.get("/api/v1/tenant/context", second.id().toString()).statusCode()).isEqualTo(200);
            jdbc.update("update users set status = 'DISABLED' where id = ?", userId);
            assertThat(browser.get("/api/v1/businesses", null).statusCode()).isEqualTo(401);
        }
    }

    private static Map<String, Long> counts() {
        var counts = new LinkedHashMap<String, Long>();
        for (var table : new String[]{"tenants", "users", "password_credentials", "memberships"}) {
            counts.put(table, jdbc.queryForObject("select count(*) from " + table, Long.class));
        }
        return counts;
    }

    private static Map<String, String> input() {
        var id = UUID.randomUUID().toString();
        return new LinkedHashMap<>(Map.of("businessName", "New Plumbing", "slug", "business-" + id,
                "ownerName", "New Owner", "email", id + "@example.com", "password", PASSWORD));
    }

    private static final class Browser implements AutoCloseable {
        private final HttpClient client = HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();

        HttpResponse<String> get(String path, String tenantId) throws Exception {
            var request = request(path).GET();
            if (tenantId != null) request.header("X-Tenant-ID", tenantId);
            return send(request);
        }

        HttpResponse<String> onboard(Map<String, String> input, boolean csrf) throws Exception {
            return post("/api/v1/onboarding", json.writeValueAsString(input), "application/json", csrf);
        }

        HttpResponse<String> login(String email, String password) throws Exception {
            return post("/api/v1/auth/login", "email=" + URLEncoder.encode(email, StandardCharsets.UTF_8)
                    + "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8), "application/x-www-form-urlencoded", true);
        }

        HttpResponse<String> post(String path, String body, String contentType, boolean csrf) throws Exception {
            var request = request(path).header("Content-Type", contentType).POST(HttpRequest.BodyPublishers.ofString(body));
            if (csrf) {
                var token = json.readTree(get("/api/v1/auth/csrf", null).body());
                request.header(token.path("headerName").asText(), token.path("token").asText());
            }
            return send(request);
        }

        private HttpRequest.Builder request(String path) {
            return HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(20));
        }
        private HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
        @Override public void close() { client.close(); }
    }
}
