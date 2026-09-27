package com.fieldops.identity.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.Email;

@Entity
@Table(name = "users", schema = "public")
public class User {
    private static final Pattern EDGE_WHITESPACE = Pattern.compile("(?U)^\\s+|\\s+$");

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @Email
    @Column(nullable = false, length = 254, updatable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected User() {
        // Required by JPA.
    }

    public static User create(String displayName, String email) {
        var user = new User();
        user.id = UUID.randomUUID();
        user.displayName = normalizeDisplayName(displayName);
        user.email = normalizeEmail(email);
        user.status = UserStatus.ACTIVE;
        user.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        user.updatedAt = user.createdAt;
        // Hibernate initializes the nullable version to zero on persistence.
        return user;
    }

    public void rename(String displayName) {
        this.displayName = normalizeDisplayName(displayName);
    }

    public void disable() {
        status = UserStatus.DISABLED;
    }

    public void reactivate() {
        status = UserStatus.ACTIVE;
    }

    @PreUpdate
    private void recordUpdate() {
        updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    private static String normalizeDisplayName(String value) {
        if (value == null) throw new IllegalArgumentException("Display name is required");
        var normalized = EDGE_WHITESPACE.matcher(value).replaceAll("");
        if (normalized.isEmpty() || normalized.codePointCount(0, normalized.length()) > 200
                || normalized.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Display name must contain 1–200 characters without control characters");
        }
        return normalized;
    }

    private static String normalizeEmail(String value) {
        if (value == null) throw new IllegalArgumentException("Email is required");
        var normalized = EDGE_WHITESPACE.matcher(value).replaceAll("");
        // FieldOps starts with ASCII email addresses. Bean Validation validates
        // address syntax in the application service and again before JPA writes.
        if (normalized.isEmpty() || normalized.length() > 254
                || normalized.chars().anyMatch(character -> character < 33 || character > 126)) {
            throw new IllegalArgumentException("Email must contain 1–254 printable ASCII characters without spaces");
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    public UUID getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getEmail() { return email; }
    public UserStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}
