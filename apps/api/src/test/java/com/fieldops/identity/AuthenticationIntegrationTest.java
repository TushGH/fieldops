package com.fieldops.identity;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import com.fieldops.FieldOpsApplication;
import com.fieldops.identity.application.PasswordCredentialService;
import com.fieldops.identity.application.UserService;
import com.fieldops.identity.domain.User;
import com.fieldops.identity.infrastructure.PasswordCredentialRepository;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class AuthenticationIntegrationTest {
    // Test-only passphrase. There are no seeded or default application credentials.
    private static final String PASSWORD = "correct horse battery staple";
    private static final String EMAIL = "login@example.com";
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.9-alpine");
    private static ConfigurableApplicationContext context;
    private static UserService users;
    private static PasswordCredentialService credentials;
    private static JdbcTemplate jdbc;
    private static ObjectMapper json;
    private static UUID userId;
    private static String baseUrl;

    @BeforeAll
    static void startApplication() {
        // Upgrade a V3 database; the other suites also exercise fresh V1–V4 migration.
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .target("3").load().migrate();
        context = SpringApplication.run(FieldOpsApplication.class,
                "--server.port=0", "--server.servlet.session.cookie.secure=false",
                "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                "--spring.datasource.username=" + POSTGRES.getUsername(),
                "--spring.datasource.password=" + POSTGRES.getPassword());
        users = context.getBean(UserService.class);
        credentials = context.getBean(PasswordCredentialService.class);
        jdbc = context.getBean(JdbcTemplate.class);
        json = context.getBean(ObjectMapper.class);
        userId = users.create("Login User", EMAIL).id();
        credentials.provision(userId, PASSWORD);
        baseUrl = "http://127.0.0.1:" + context.getEnvironment().getRequiredProperty("local.server.port");
    }

    @AfterAll
    static void stopApplication() {
        if (context != null) context.close();
    }

    @Test
    void loginRotatesSessionAndReturnsOnlyCurrentProfileWithoutTenantClaims() throws Exception {
        try (var browser = new Browser()) {
            assertThat(browser.get("/api/v1/auth/me").statusCode()).isEqualTo(401);
            var csrf = browser.csrf();
            var anonymousSession = browser.sessionId();
            var response = browser.login(" LOGIN@EXAMPLE.COM ", PASSWORD, csrf);
            assertThat(response.statusCode()).isEqualTo(204);
            assertThat(response.body()).isEmpty();
            assertThat(browser.sessionId()).isNotEqualTo(anonymousSession);
            assertThat(response.headers().allValues("set-cookie").toString()).contains("HttpOnly", "SameSite=Lax");
            var me = browser.get("/api/v1/auth/me");
            assertThat(me.statusCode()).isEqualTo(200);
            assertThat(me.headers().firstValue("cache-control")).hasValue("no-store");
            var body = json.readTree(me.body());
            assertThat(body.size()).isEqualTo(5);
            assertThat(body.path("emailVerifiedAt").isNull()).isTrue();
            assertThat(body.path("platformAdmin").asBoolean()).isFalse();
            assertThat(body.path("id").asText()).isEqualTo(userId.toString());
            assertThat(body.path("email").asText()).isEqualTo(EMAIL);
            assertThat(body.path("displayName").asText()).isEqualTo("Login User");
            assertThat(me.body()).doesNotContain("password", "bcrypt", "tenant", "role");
            try (var otherBrowser = new Browser()) {
                assertThat(otherBrowser.get("/api/v1/auth/me").statusCode()).isEqualTo(401);
                assertThat(otherBrowser.getWithSession("/api/v1/auth/me", anonymousSession).statusCode()).isEqualTo(401);
            }
        }
    }

    @Test
    void requiresCsrfForLoginAndLogoutAndRejectsOldTokensAfterLogin() throws Exception {
        try (var browser = new Browser()) {
            assertThat(browser.login(EMAIL, PASSWORD, null).statusCode()).isEqualTo(403);
            var csrf = browser.csrf();
            assertThat(browser.login(EMAIL, PASSWORD, new Token(csrf.header(), "invalid")).statusCode()).isEqualTo(403);
            assertThat(browser.login(EMAIL, PASSWORD, csrf).statusCode()).isEqualTo(204);
            assertThat(browser.post("/api/v1/auth/logout", "", null).statusCode()).isEqualTo(403);
            assertThat(browser.post("/api/v1/auth/logout", "", csrf).statusCode()).isEqualTo(403);
            assertThat(browser.get("/api/v1/auth/me").statusCode()).isEqualTo(200);
            var fresh = browser.csrf();
            assertThat(browser.post("/api/v1/auth/logout", "", fresh).statusCode()).isEqualTo(204);
        }
    }

    @Test
    void logoutInvalidatesSessionAndCannotBePerformedWithGet() throws Exception {
        try (var browser = new Browser()) {
            assertThat(browser.login(EMAIL, PASSWORD, browser.csrf()).statusCode()).isEqualTo(204);
            var sessionId = browser.sessionId();
            assertThat(browser.get("/api/v1/auth/logout").statusCode()).isNotEqualTo(204);
            assertThat(browser.get("/api/v1/auth/me").statusCode()).isEqualTo(200);
            var logout = browser.post("/api/v1/auth/logout", "", browser.csrf());
            assertThat(logout.statusCode()).isEqualTo(204);
            assertThat(logout.headers().allValues("set-cookie").stream()
                    .flatMap(value -> HttpCookie.parse(value).stream())
                    .anyMatch(cookie -> cookie.getName().equals("FIELDOPS_SESSION") && cookie.hasExpired())).isTrue();
            assertThat(browser.get("/api/v1/auth/me").statusCode()).isEqualTo(401);
            try (var replay = new Browser()) {
                assertThat(replay.getWithSession("/api/v1/auth/me", sessionId).statusCode()).isEqualTo(401);
            }
        }
    }

    @Test
    void invalidCredentialsHaveSameResponseForUnknownUnprovisionedAndDisabledUsers() throws Exception {
        var disabled = users.create("Disabled", "disabled@example.com");
        credentials.provision(disabled.id(), PASSWORD);
        disable(disabled.id());
        users.create("Unprovisioned", "unprovisioned@example.com");
        String expected = null;
        for (var email : new String[]{EMAIL, "unknown@example.com", "unprovisioned@example.com", "disabled@example.com", "not-an-email"}) {
            try (var browser = new Browser()) {
                var response = browser.login(email, email.equals(EMAIL) ? "wrong-password-value" : PASSWORD, browser.csrf());
                assertThat(response.statusCode()).isEqualTo(401);
                assertThat(response.headers().firstValue("location")).isEmpty();
                if (expected == null) expected = response.body();
                assertThat(response.body()).isEqualTo(expected);
                assertThat(browser.get("/api/v1/auth/me").statusCode()).isEqualTo(401);
            }
        }
    }

    @Test
    void failedReloginDoesNotRetainPreviousAuthenticatedSession() throws Exception {
        try (var browser = new Browser()) {
            assertThat(browser.login(EMAIL, PASSWORD, browser.csrf()).statusCode()).isEqualTo(204);
            var sessionId = browser.sessionId();
            assertThat(browser.login(EMAIL, "incorrect-passphrase", browser.csrf()).statusCode()).isEqualTo(401);
            assertThat(browser.get("/api/v1/auth/me").statusCode()).isEqualTo(401);
            try (var replay = new Browser()) {
                assertThat(replay.getWithSession("/api/v1/auth/me", sessionId).statusCode()).isEqualTo(401);
            }
        }
    }

    @Test
    void disablingAUserInvalidatesTheirExistingSessionOnNextRequest() throws Exception {
        var user = users.create("Disable After Login", "disable-after@example.com");
        credentials.provision(user.id(), PASSWORD);
        try (var browser = new Browser()) {
            assertThat(browser.login(user.email(), PASSWORD, browser.csrf()).statusCode()).isEqualTo(204);
            disable(user.id());
            assertThat(browser.get("/api/v1/auth/me").statusCode()).isEqualTo(401);
            assertThat(browser.login(user.email(), PASSWORD, browser.csrf()).statusCode()).isEqualTo(401);
        }
    }

    @Test
    void hashIsSaltedAndCredentialProvisioningCannotOverwriteExistingPasswords() {
        var repository = context.getBean(PasswordCredentialRepository.class);
        var encoder = context.getBean(PasswordEncoder.class);
        var stored = repository.findById(userId).orElseThrow().getPasswordHash();
        assertThat(stored).startsWith("{bcrypt}$2a$12$").doesNotContain(PASSWORD);
        assertThat(encoder.matches(PASSWORD, stored)).isTrue();
        var other = users.create("Other Hash", "other-hash@example.com");
        credentials.provision(other.id(), PASSWORD);
        assertThat(repository.findById(other.id()).orElseThrow().getPasswordHash()).isNotEqualTo(stored);
        assertThatThrownBy(() -> credentials.provision(userId, "different secure passphrase"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(repository.findById(userId).orElseThrow().getPasswordHash()).isEqualTo(stored);
        assertThatThrownBy(() -> credentials.provision(UUID.randomUUID(), PASSWORD))
                .isInstanceOf(DataIntegrityViolationException.class).hasStackTraceContaining("fk_password_credentials_user");
        assertThatThrownBy(() -> jdbc.update("UPDATE password_credentials SET password_hash = 'plaintext' WHERE user_id = ?", userId))
                .isInstanceOf(DataIntegrityViolationException.class);
        context.getBean(Flyway.class).validate();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"short", "fourteen-char!"})
    void rejectsWeakInitialPasswords(String password) {
        assertThatIllegalArgumentException().isThrownBy(() -> credentials.provision(userId, password));
    }

    @Test
    void enforcesBcryptByteLimitAtProvisioningAndLoginWithoutTruncation() throws Exception {
        assertThatIllegalArgumentException().isThrownBy(() -> credentials.provision(userId, "x".repeat(73)));
        assertThatIllegalArgumentException().isThrownBy(() -> credentials.provision(userId, "é".repeat(37)));
        var user = users.create("Byte Boundary", "byte-boundary@example.com");
        var password = "é".repeat(36);
        credentials.provision(user.id(), password);
        try (var browser = new Browser()) {
            assertThat(browser.login(user.email(), password, browser.csrf()).statusCode()).isEqualTo(204);
            assertThat(browser.login(user.email(), password + "x", browser.csrf()).statusCode()).isEqualTo(401);
        }
        var encoder = context.getBean(PasswordEncoder.class);
        var hash = context.getBean(PasswordCredentialRepository.class).findById(user.id()).orElseThrow().getPasswordHash();
        assertThat(encoder.matches(password + "x", hash)).isFalse();
    }

    @Test
    void healthRemainsPublicAndHttpBasicIsNotEnabled() throws Exception {
        try (var browser = new Browser()) {
            assertThat(browser.get("/api/v1/health").statusCode()).isEqualTo(200);
            assertThat(browser.get("/actuator/health").statusCode()).isEqualTo(200);
            var response = browser.client.send(HttpRequest.newBuilder(URI.create(baseUrl + "/api/v1/auth/me"))
                    .header("Authorization", "Basic " + java.util.Base64.getEncoder()
                            .encodeToString((EMAIL + ":" + PASSWORD).getBytes(StandardCharsets.UTF_8)))
                    .GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(401);
            assertThat(response.headers().firstValue("www-authenticate")).isEmpty();
        }
    }

    private static void disable(UUID id) {
        try (var em = context.getBean(EntityManagerFactory.class).createEntityManager()) {
            em.getTransaction().begin();
            em.find(User.class, id).disable();
            em.getTransaction().commit();
        }
    }

    private record Token(String header, String value) { }

    private static final class Browser implements AutoCloseable {
        private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        private final HttpClient client = HttpClient.newBuilder().cookieHandler(cookies)
                .followRedirects(HttpClient.Redirect.NEVER).build();

        HttpResponse<String> get(String path) throws Exception {
            return client.send(request(path).GET().build(), HttpResponse.BodyHandlers.ofString());
        }

        HttpResponse<String> getWithSession(String path, String sessionId) throws Exception {
            return client.send(request(path).header("Cookie", "FIELDOPS_SESSION=" + sessionId).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
        }

        Token csrf() throws Exception {
            var response = get("/api/v1/auth/csrf");
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue("cache-control")).hasValue("no-store");
            JsonNode body = json.readTree(response.body());
            return new Token(body.path("headerName").asText(), body.path("token").asText());
        }

        HttpResponse<String> login(String email, String password, Token csrf) throws Exception {
            return post("/api/v1/auth/login", "email=" + URLEncoder.encode(email, StandardCharsets.UTF_8)
                    + "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8), csrf);
        }

        HttpResponse<String> post(String path, String body, Token csrf) throws Exception {
            var request = request(path).header("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            if (csrf != null) request.header(csrf.header(), csrf.value());
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }

        String sessionId() {
            return cookies.getCookieStore().getCookies().stream().filter(cookie -> cookie.getName().equals("FIELDOPS_SESSION"))
                    .map(HttpCookie::getValue).findFirst().orElseThrow();
        }

        private HttpRequest.Builder request(String path) {
            return HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(10));
        }

        @Override
        public void close() { client.close(); }
    }
}
