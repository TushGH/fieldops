package com.fieldops.identity;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import com.fieldops.FieldOpsApplication;
import com.fieldops.identity.application.PasswordCredentialService;
import com.fieldops.identity.application.UserService;
import com.fieldops.identity.infrastructure.LinkTokens;
import org.junit.jupiter.api.*;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class IdentityOnboardingIntegrationTest {
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.9-alpine");
    private static ConfigurableApplicationContext context;
    private static JdbcTemplate jdbc;
    private static ObjectMapper json;
    private static String base;
    private static final String PASSWORD = "a sufficiently long password";
    private static final Mailbox MAIL = new Mailbox();

    @BeforeAll static void start() {
        var app = new SpringApplication(FieldOpsApplication.class);
        app.addInitializers(ctx -> ctx.getBeanFactory().registerSingleton("mailSender", MAIL));
        context = app.run("--server.port=0", "--server.servlet.session.cookie.secure=false", "--fieldops.platform-enabled=true",
                "--spring.datasource.url=" + POSTGRES.getJdbcUrl(), "--spring.datasource.username=" + POSTGRES.getUsername(),
                "--spring.datasource.password=" + POSTGRES.getPassword());
        jdbc = context.getBean(JdbcTemplate.class); json = context.getBean(ObjectMapper.class);
        base = "http://127.0.0.1:" + context.getEnvironment().getRequiredProperty("local.server.port") + "/api/v1/";
    }
    @AfterAll static void stop() { if (context != null) context.close(); }

    @Test void emailFirstRegistrationIsSingleUseAndCannotOverwriteAnExistingCredential() throws Exception {
        var email = email();
        try (var browser = new Browser()) {
            assertThat(browser.post("auth/signup", Map.of("displayName", "Owner", "email", email.toUpperCase()), null).statusCode()).isEqualTo(202);
            assertThat(count("users", "email", email)).isZero();
            var token = MAIL.token(email);
            assertThat(jdbc.queryForObject("SELECT token_hash FROM email_challenges WHERE email = ?", String.class, email)).isEqualTo(LinkTokens.hash(token)).doesNotContain(token);
            assertThat(browser.post("auth/signup/complete", Map.of("token", token, "password", PASSWORD), null).statusCode()).isEqualTo(204);
            assertThat(browser.post("auth/signup/complete", Map.of("token", token, "password", "replacement password value"), null).statusCode()).isEqualTo(400);
            assertThat(browser.post("auth/signup", Map.of("displayName", "Attacker", "email", email), null).statusCode()).isEqualTo(202);
            assertThat(count("users", "email", email)).isEqualTo(1);
            browser.login(email);
            var me = browser.body(browser.get("auth/me", null));
            assertThat(me.path("emailVerifiedAt").isNull()).isFalse();
            assertThat(me.path("platformAdmin").asBoolean()).isFalse();
            var a = browser.business("First"); var b = browser.business("Second");
            assertThat(a).isNotEqualTo(b);
            assertThat(browser.body(browser.get("businesses", null)).size()).isEqualTo(2);
            assertThat(count("memberships", "user_id", UUID.fromString(me.path("id").asText()))).isEqualTo(2);
        }
    }

    @Test void legacyAccountNeedsMatchingVerificationAndExpiredChallengesFail() throws Exception {
        var email = email(); var user = createUser(email, false);
        try (var browser = new Browser(); var wrong = account()) {
            browser.login(email);
            assertThat(browser.post("businesses", Map.of("name", "Blocked", "slug", "blocked"), null).statusCode()).isEqualTo(403);
            assertThat(browser.post("auth/email-verification/request", null, null).statusCode()).isEqualTo(202);
            var token = MAIL.token(email);
            assertThat(wrong.post("auth/email-verification/confirm", Map.of("token", token), null).statusCode()).isEqualTo(400);
            assertThat(browser.post("auth/email-verification/confirm", Map.of("token", token), null).statusCode()).isEqualTo(204);
            assertThat(jdbc.queryForObject("SELECT email_verified_at IS NOT NULL FROM users WHERE id = ?", Boolean.class, user)).isTrue();
            assertThat(browser.post("auth/email-verification/confirm", Map.of("token", token), null).statusCode()).isEqualTo(400);
        }
        try (var browser = new Browser()) {
            var fresh = email(); browser.post("auth/signup", Map.of("displayName", "Expired", "email", fresh), null);
            jdbc.update("UPDATE email_challenges SET created_at = now() - interval '2 days', expires_at = now() - interval '1 day' WHERE email = ?", fresh);
            assertThat(browser.post("auth/signup/complete", Map.of("token", MAIL.token(fresh), "password", PASSWORD), null).statusCode()).isEqualTo(400);
            assertThat(count("users", "email", fresh)).isZero();
        }
    }

    @Test void invitesExistingAndNewUsersWithoutDuplicatingIdentityAndRejectsWrongAccount() throws Exception {
        try (var owner = account(); var recipient = account(); var wrong = account()) {
            var tenant = owner.business("Team");
            var invitation = owner.invite(tenant, recipient.email, "DISPATCHER");
            assertThat(count("memberships", "tenant_id", tenant)).isEqualTo(1);
            assertThat(wrong.post("invitations/" + invitation + "/accept", null, null).statusCode()).isEqualTo(404);
            assertThat(recipient.post("invitations/" + invitation + "/accept", null, null).statusCode()).isEqualTo(200);
            assertThat(recipient.post("invitations/" + invitation + "/accept", null, null).statusCode()).isEqualTo(200);
            assertThat(count("users", "email", recipient.email)).isEqualTo(1);
            assertThat(count("memberships", "tenant_id", tenant)).isEqualTo(2);
            assertThat(recipient.get("tenant/context", tenant).statusCode()).isEqualTo(200);
            assertThat(recipient.post("tenant/invitations", Map.of("email", wrong.email, "role", "BUSINESS_OWNER"), tenant).statusCode()).isEqualTo(403);
            var fresh = email(); var newInvitation = owner.invite(tenant, fresh, "TECHNICIAN");
            assertThat(count("users", "email", fresh)).isZero();
            try (var newcomer = new Browser()) {
                var reference = newcomer.body(newcomer.post("invitations/resolve", Map.of("token", MAIL.token(fresh)), null));
                assertThat(reference.size()).isEqualTo(1);
                newcomer.post("auth/signup", Map.of("displayName", "New teammate", "email", fresh), null);
                newcomer.post("auth/signup/complete", Map.of("token", MAIL.token(fresh), "password", PASSWORD), null);
                newcomer.login(fresh);
                assertThat(newcomer.post("invitations/" + newInvitation + "/accept", null, null).statusCode()).isEqualTo(200);
            }
        }
    }

    @Test void invitationCannotReactivateMembershipOrOverwriteItsRole() throws Exception {
        try (var owner = account(); var recipient = account()) {
            var tenant = owner.business("Role safety");
            var first = owner.invite(tenant, recipient.email, "TECHNICIAN");
            recipient.post("invitations/" + first + "/accept", null, null);
            var second = owner.invite(tenant, recipient.email, "BUSINESS_OWNER");
            assertThat(recipient.post("invitations/" + second + "/accept", null, null).statusCode()).isEqualTo(200);
            assertThat(recipient.body(recipient.get("tenant/context", tenant)).path("role").asText()).isEqualTo("TECHNICIAN");
            jdbc.update("UPDATE memberships SET status = 'INACTIVE' WHERE tenant_id = ? AND user_id = ?", tenant, recipient.userId);
            var third = owner.invite(tenant, recipient.email, "BUSINESS_OWNER");
            assertThat(recipient.post("invitations/" + third + "/accept", null, null).statusCode()).isEqualTo(409);
            assertThat(recipient.get("tenant/context", tenant).statusCode()).isEqualTo(403);
        }
    }

    @Test void revokedExpiredSuspendedAndUnauthorizedIssuerInvitationsFail() throws Exception {
        try (var owner = account(); var recipient = account()) {
            for (var reason : List.of("revoked", "expired", "suspended", "issuer")) {
                var tenant = owner.business(reason); var invitation = owner.invite(tenant, recipient.email, "TECHNICIAN");
                switch (reason) {
                    case "revoked" -> owner.post("tenant/invitations/" + invitation + "/revoke", null, tenant);
                    case "expired" -> jdbc.update("UPDATE invitations SET created_at = now() - interval '9 days', expires_at = now() - interval '1 day' WHERE id = ?", invitation);
                    case "suspended" -> jdbc.update("UPDATE tenants SET status = 'SUSPENDED' WHERE id = ?", tenant);
                    case "issuer" -> jdbc.update("UPDATE memberships SET role = 'TECHNICIAN' WHERE tenant_id = ? AND user_id = ?", tenant, owner.userId);
                }
                assertThat(recipient.post("invitations/" + invitation + "/accept", null, null).statusCode()).isIn(403, 409);
                assertThat(count("memberships", "tenant_id", tenant)).isEqualTo(1);
            }
        }
    }

    @Test void concurrentAcceptanceCreatesExactlyOneMembership() throws Exception {
        try (var owner = account(); var recipient = account(); var second = new Browser(); var executor = Executors.newFixedThreadPool(2)) {
            second.login(recipient.email);
            var tenant = owner.business("Concurrent"); var invitation = owner.invite(tenant, recipient.email, "TECHNICIAN");
            var start = new CountDownLatch(1);
            var a = executor.submit(() -> { start.await(); return recipient.post("invitations/" + invitation + "/accept", null, null).statusCode(); });
            var b = executor.submit(() -> { start.await(); return second.post("invitations/" + invitation + "/accept", null, null).statusCode(); });
            start.countDown();
            assertThat(a.get(20, TimeUnit.SECONDS)).isEqualTo(200); assertThat(b.get(20, TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(count("memberships", "tenant_id", tenant)).isEqualTo(2);
        }
    }

    @Test void onboardingAndPlatformAuthorityRemainScoped() throws Exception {
        try (var admin = account(); var owner = account()) {
            jdbc.update("INSERT INTO platform_role_grants(id, user_id, role, status, granted_at, version) VALUES (?, ?, 'PLATFORM_ADMIN', 'ACTIVE', now(), 0)", UUID.randomUUID(), admin.userId);
            var response = admin.post("platform/businesses", Map.of("name", "Assisted", "slug", "assisted-" + UUID.randomUUID(), "email", owner.email), null);
            assertThat(response.statusCode()).isEqualTo(201);
            var invitation = admin.body(response); var tenant = UUID.fromString(invitation.path("tenantId").asText());
            assertThat(count("memberships", "tenant_id", tenant)).isZero();
            assertThat(admin.get("tenant/context", tenant).statusCode()).isEqualTo(403);
            assertThat(owner.get("tenant/context", tenant).statusCode()).isEqualTo(403);
            assertThat(owner.post("invitations/" + invitation.path("id").asText() + "/accept", null, null).statusCode()).isEqualTo(200);
            assertThat(admin.get("tenant/context", tenant).statusCode()).isEqualTo(403);
            assertThat(owner.get("platform/access", null).statusCode()).isEqualTo(403);
            var other = owner.business("Other");
            assertThat(owner.post("tenant/onboarding/business/complete", null, tenant).statusCode()).isEqualTo(200);
            assertThat(owner.body(owner.get("tenant/onboarding", tenant)).path("businessComplete").asBoolean()).isTrue();
            assertThat(owner.body(owner.get("tenant/onboarding", other)).path("businessComplete").asBoolean()).isFalse();
        }
    }

    @Test void concurrentNormalizedRegistrationHasOneWinnerAndNoCredentialReplacement() throws Exception {
        var email = email();
        try (var first = new Browser(); var second = new Browser(); var executor = Executors.newFixedThreadPool(2)) {
            first.post("auth/signup", Map.of("displayName", "First", "email", email), null);
            var aToken = MAIL.token(email);
            second.post("auth/signup", Map.of("displayName", "Second", "email", email.toUpperCase()), null);
            var bToken = MAIL.token(email);
            var start = new CountDownLatch(1);
            var a = executor.submit(() -> { start.await(); return first.post("auth/signup/complete", Map.of("token", aToken, "password", PASSWORD), null).statusCode(); });
            var b = executor.submit(() -> { start.await(); return second.post("auth/signup/complete", Map.of("token", bToken, "password", PASSWORD), null).statusCode(); });
            start.countDown();
            assertThat(List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS))).containsExactlyInAnyOrder(204, 409);
            assertThat(count("users", "email", email)).isEqualTo(1);
            first.login(email);
        }
    }

    @Test void revocationAndAcceptanceRaceCannotResurrectRevokedInvitation() throws Exception {
        try (var owner = account(); var recipient = account(); var executor = Executors.newFixedThreadPool(2)) {
            var tenant = owner.business("Revoke race"); var invitation = owner.invite(tenant, recipient.email, "TECHNICIAN");
            var start = new CountDownLatch(1);
            var a = executor.submit(() -> { start.await(); return owner.post("tenant/invitations/" + invitation + "/revoke", null, tenant).statusCode(); });
            var b = executor.submit(() -> { start.await(); return recipient.post("invitations/" + invitation + "/accept", null, null).statusCode(); });
            start.countDown();
            assertThat(a.get(20, TimeUnit.SECONDS)).isEqualTo(204);
            var accepted = b.get(20, TimeUnit.SECONDS);
            assertThat(accepted).isIn(200, 409);
            assertThat(jdbc.queryForObject("SELECT status FROM invitations WHERE id = ?", String.class, invitation))
                    .isEqualTo(accepted == 200 ? "ACCEPTED" : "REVOKED");
            assertThat(count("memberships", "tenant_id", tenant)).isEqualTo(accepted == 200 ? 2 : 1);
        }
    }

    @Test void revokedPlatformGrantPreventsOwnerAcceptanceAndReissueInvalidatesOldOffer() throws Exception {
        try (var admin = account(); var owner = account()) {
            var grant = UUID.randomUUID();
            jdbc.update("INSERT INTO platform_role_grants(id, user_id, role, status, granted_at, version) VALUES (?, ?, 'PLATFORM_ADMIN', 'ACTIVE', now(), 0)", grant, admin.userId);
            var created = admin.body(admin.post("platform/businesses", Map.of("name", "Provisioned", "slug", "provisioned-" + UUID.randomUUID(), "email", owner.email), null));
            var tenant = UUID.fromString(created.path("tenantId").asText());
            var replacement = admin.body(admin.post("platform/businesses/" + tenant + "/owner-invitation", Map.of("email", owner.email), null));
            assertThat(owner.post("invitations/" + created.path("id").asText() + "/accept", null, null).statusCode()).isEqualTo(409);
            jdbc.update("UPDATE platform_role_grants SET status = 'REVOKED', revoked_at = now(), revoked_by_user_id = ? WHERE id = ?", admin.userId, grant);
            assertThat(owner.post("invitations/" + replacement.path("id").asText() + "/accept", null, null).statusCode()).isEqualTo(409);
            assertThat(count("memberships", "tenant_id", tenant)).isZero();
            assertThat(jdbc.queryForObject("SELECT status FROM tenants WHERE id = ?", String.class, tenant)).isEqualTo("PROVISIONING");
        }
    }

    @Test void failedMailDeliveryCommitsChallengeAndResendRecovers() throws Exception {
        var email = email(); MAIL.failures.add(email);
        try (var browser = new Browser()) {
            assertThat(browser.post("auth/signup", Map.of("displayName", "Mail failure", "email", email), null).statusCode()).isEqualTo(202);
            assertThat(count("email_challenges", "email", email)).isEqualTo(1);
            assertThat(MAIL.messages).doesNotContainKey(email);
            MAIL.failures.remove(email);
            assertThat(browser.post("auth/signup", Map.of("displayName", "Mail recovery", "email", email), null).statusCode()).isEqualTo(202);
            assertThat(browser.post("auth/signup/complete", Map.of("token", MAIL.token(email), "password", PASSWORD), null).statusCode()).isEqualTo(204);
        } finally { MAIL.failures.remove(email); }
    }

    @Test void businessCreationRollsBackOnSlugConflictAndRequiresCsrf() throws Exception {
        try (var first = account(); var second = account()) {
            var tenant = first.business("Unique");
            var slug = first.body(first.get("tenant", tenant)).path("slug").asText();
            assertThat(second.post("businesses", Map.of("name", "Collision", "slug", slug), null).statusCode()).isEqualTo(409);
            assertThat(count("memberships", "user_id", second.userId)).isZero();
            var request = HttpRequest.newBuilder(URI.create(base + "businesses")).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"CSRF\",\"slug\":\"csrf-test\"}")).build();
            assertThat(second.client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(403);
        }
    }

    @Test void legacyCombinedSignupCannotBypassEmailVerification() throws Exception {
        var email = email();
        var input = Map.of("businessName", "Legacy Signup", "slug", "legacy-" + UUID.randomUUID(),
                "ownerName", "Legacy Owner", "email", email, "password", PASSWORD);
        try (var anonymous = new Browser(); var signedIn = account()) {
            assertThat(anonymous.post("onboarding", input, null).statusCode()).isEqualTo(401);
            assertThat(signedIn.post("onboarding", input, null).statusCode()).isEqualTo(404);
            assertThat(count("users", "email", email)).isZero();
            assertThat(count("tenants", "slug", input.get("slug"))).isZero();
        }
    }

    private static String email() { return UUID.randomUUID() + "@example.com"; }
    private static int count(String table, String column, Object value) { return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE " + column + " = ?", Integer.class, value); }
    private static UUID createUser(String email, boolean verified) {
        var user = context.getBean(UserService.class).create("Test User", email);
        context.getBean(PasswordCredentialService.class).provision(user.id(), PASSWORD);
        if (verified) jdbc.update("UPDATE users SET email_verified_at = now() WHERE id = ?", user.id());
        return user.id();
    }
    private static Browser account() throws Exception {
        var browser = new Browser(); browser.email = email(); browser.userId = createUser(browser.email, true); browser.login(browser.email); return browser;
    }
    static class Mailbox extends JavaMailSenderImpl {
        final Map<String, String> messages = new ConcurrentHashMap<>();
        final java.util.Set<String> failures = ConcurrentHashMap.newKeySet();
        @Override public void send(SimpleMailMessage message) {
            if (failures.contains(message.getTo()[0])) throw new org.springframework.mail.MailSendException("Simulated SMTP outage");
            messages.put(message.getTo()[0], message.getText()); }
        String token(String email) { return messages.get(email).split("#token=")[1].split("\\s")[0]; }
    }
    private static class Browser implements AutoCloseable {
        final HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
        String email; UUID userId;
        void login(String email) throws Exception {
            var csrf = body(get("auth/csrf", null));
            var response = client.send(HttpRequest.newBuilder(URI.create(base + "auth/login"))
                    .header("X-CSRF-TOKEN", csrf.path("token").asText()).header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString("email=" + URLEncoder.encode(email, StandardCharsets.UTF_8) + "&password=" + URLEncoder.encode(PASSWORD, StandardCharsets.UTF_8))).build(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(204);
        }
        HttpResponse<String> get(String path, UUID tenant) throws Exception {
            var request = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(10));
            if (tenant != null) request.header("X-Tenant-ID", tenant.toString());
            return client.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
        }
        HttpResponse<String> post(String path, Object data, UUID tenant) throws Exception {
            var csrf = body(get("auth/csrf", null));
            var request = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(15))
                    .header("X-CSRF-TOKEN", csrf.path("token").asText()).header("Content-Type", "application/json");
            if (tenant != null) request.header("X-Tenant-ID", tenant.toString());
            return client.send(request.POST(HttpRequest.BodyPublishers.ofString(data == null ? "" : json.writeValueAsString(data))).build(), HttpResponse.BodyHandlers.ofString());
        }
        JsonNode body(HttpResponse<String> response) { return json.readTree(response.body()); }
        UUID business(String name) throws Exception {
            var response = post("businesses", Map.of("name", name, "slug", "business-" + UUID.randomUUID()), null);
            assertThat(response.statusCode()).isEqualTo(201);
            return UUID.fromString(body(response).path("id").asText());
        }
        UUID invite(UUID tenant, String email, String role) throws Exception {
            var response = post("tenant/invitations", Map.of("email", email, "role", role), tenant);
            assertThat(response.statusCode()).isEqualTo(201);
            return UUID.fromString(body(response).path("id").asText());
        }
        @Override public void close() { client.close(); }
    }
}
