package com.fieldops.identity.application;

import java.time.Instant;
import java.util.UUID;

import com.fieldops.identity.domain.User;
import com.fieldops.identity.domain.UserStatus;

public record UserDetails(UUID id, String displayName, String email, UserStatus status,
                          Instant createdAt, Instant updatedAt, long version, Instant emailVerifiedAt) {
    static UserDetails from(User user) {
        return new UserDetails(user.getId(), user.getDisplayName(), user.getEmail(), user.getStatus(),
                user.getCreatedAt(), user.getUpdatedAt(), user.getVersion(), user.getEmailVerifiedAt());
    }
}
