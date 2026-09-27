package com.fieldops.tenant.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "memberships", schema = "public")
public class Membership {
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MembershipStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected Membership() {
        // Required by JPA.
    }

    public static Membership create(UUID tenantId, UUID userId) {
        var membership = new Membership();
        membership.id = UUID.randomUUID();
        membership.tenantId = Objects.requireNonNull(tenantId, "Tenant ID is required");
        membership.userId = Objects.requireNonNull(userId, "User ID is required");
        membership.status = MembershipStatus.ACTIVE;
        membership.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        membership.updatedAt = membership.createdAt;
        return membership;
    }

    public void deactivate() {
        status = MembershipStatus.INACTIVE;
    }

    public void reactivate() {
        status = MembershipStatus.ACTIVE;
    }

    @PreUpdate
    private void recordUpdate() {
        updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getUserId() { return userId; }
    public MembershipStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}
