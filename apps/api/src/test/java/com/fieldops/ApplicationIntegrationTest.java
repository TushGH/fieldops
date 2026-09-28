package com.fieldops;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class ApplicationIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.9-alpine");

    private static ConfigurableApplicationContext context;

    // Spring 7's SpringExtension requires JUnit 6. Boot the real application
    // directly so this foundation can honor its explicit JUnit 5 requirement.
    @BeforeAll
    static void startApplication() {
        context = SpringApplication.run(FieldOpsApplication.class,
                "--server.port=0",
                "--server.servlet.session.cookie.secure=true",
                "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                "--spring.datasource.username=" + POSTGRES.getUsername(),
                "--spring.datasource.password=" + POSTGRES.getPassword());
    }

    @AfterAll
    static void stopApplication() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void startsApplicationAndServesHealthOverHttp() throws Exception {
        var response = get("/api/v1/health");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("content-type")).hasValueSatisfying(
                value -> assertThat(value).startsWith("application/json"));
        var body = context.getBean(ObjectMapper.class).readTree(response.body());
        assertThat(body.path("application").asText()).isEqualTo("fieldops-api");
        assertThat(body.path("status").asText()).isEqualTo("UP");
    }

    @Test
    void connectsToPostgresAndAppliesMigrations() {
        var jdbc = context.getBean(JdbcTemplate.class);
        var flyway = context.getBean(Flyway.class);
        assertThat(jdbc.queryForObject("SELECT 1", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT version()", String.class)).startsWith("PostgreSQL 17.");
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("8");
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '1' AND success",
                Integer.class)).isEqualTo(1);
        flyway.validate();
    }

    @Test
    void exposesAggregateActuatorHealthWithoutInternalDetails() throws Exception {
        var response = get("/actuator/health");

        assertThat(response.statusCode()).isEqualTo(200);
        var body = context.getBean(ObjectMapper.class).readTree(response.body());
        assertThat(body.path("status").asText()).isEqualTo("UP");
        assertThat(body.has("components")).isFalse();
    }

    @Test
    void usesSecureHttpOnlySessionCookiesWhenConfiguredForHttps() throws Exception {
        var response = get("/api/v1/auth/csrf");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().allValues("set-cookie").toString())
                .contains("Secure", "HttpOnly", "SameSite=Lax");
    }

    private HttpResponse<String> get(String path) throws Exception {
        var port = context.getEnvironment().getRequiredProperty("local.server.port");
        try (var client = HttpClient.newHttpClient()) {
            return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                    .timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
