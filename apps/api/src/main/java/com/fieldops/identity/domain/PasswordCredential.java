package com.fieldops.identity.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Encoded credential only; never expose this entity in an API response. */
@Entity
@Table(name = "password_credentials", schema = "public")
public class PasswordCredential {
    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "password_hash", nullable = false, length = 255, updatable = false)
    private String passwordHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected PasswordCredential() {
        // Required by JPA.
    }

    public PasswordCredential(UUID userId, String passwordHash) {
        this.userId = Objects.requireNonNull(userId, "User ID is required");
        this.passwordHash = Objects.requireNonNull(passwordHash, "Encoded password is required");
        this.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public UUID getUserId() { return userId; }
    public String getPasswordHash() { return passwordHash; }
}
