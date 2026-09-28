package com.fieldops.tenant.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "tenants", schema = "public")
public class Tenant {
    private static final Pattern SLUG = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");
    private static final Pattern EDGE_WHITESPACE = Pattern.compile("(?U)^\\s+|\\s+$");

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 63, updatable = false)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TenantStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected Tenant() {
        // Required by JPA.
    }

    public static Tenant create(String name, String slug) {
        var tenant = new Tenant();
        tenant.id = UUID.randomUUID();
        tenant.name = normalizeName(name);
        tenant.slug = normalizeSlug(slug);
        tenant.status = TenantStatus.ACTIVE;
        tenant.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        tenant.updatedAt = tenant.createdAt;
        // A null version marks this assigned-ID entity as new to Spring Data.
        // Hibernate initializes it to zero on persistence.
        return tenant;
    }

    public static Tenant provision(String name, String slug) {
        var tenant = create(name, slug);
        tenant.status = TenantStatus.PROVISIONING;
        return tenant;
    }

    public void rename(String name) {
        this.name = normalizeName(name);
    }

    public void suspend() {
        status = TenantStatus.SUSPENDED;
    }

    public void reactivate() {
        if (status == TenantStatus.PROVISIONING) throw new IllegalStateException("Accept the initial owner invitation first");
        status = TenantStatus.ACTIVE;
    }

    @PreUpdate
    private void recordUpdate() {
        updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    private static String normalizeName(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Tenant name is required");
        }
        var normalized = EDGE_WHITESPACE.matcher(value).replaceAll("");
        if (normalized.isEmpty() || normalized.codePointCount(0, normalized.length()) > 200
                || normalized.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Tenant name must contain 1–200 characters without control characters");
        }
        return normalized;
    }

    private static String normalizeSlug(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Tenant slug is required");
        }
        var normalized = value.strip().toLowerCase(Locale.ROOT);
        if (normalized.length() < 3 || normalized.length() > 63 || !SLUG.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Tenant slug must contain 3–63 lowercase letters, digits, or single separating hyphens");
        }
        return normalized;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getSlug() { return slug; }
    public TenantStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}
