package com.fieldops.tenant.application;

import java.time.Instant;
import java.util.UUID;

import com.fieldops.tenant.domain.Membership;
import com.fieldops.tenant.domain.MembershipStatus;

public record MembershipDetails(UUID id, UUID tenantId, UUID userId, MembershipStatus status,
                                Instant createdAt, Instant updatedAt, long version) {
    static MembershipDetails from(Membership membership) {
        return new MembershipDetails(membership.getId(), membership.getTenantId(), membership.getUserId(),
                membership.getStatus(), membership.getCreatedAt(), membership.getUpdatedAt(), membership.getVersion());
    }
}
