package com.fieldops.tenant.application;

import java.time.Instant;
import java.util.UUID;

import com.fieldops.tenant.domain.Tenant;
import com.fieldops.tenant.domain.TenantStatus;

public record TenantDetails(UUID id, String name, String slug, TenantStatus status,
                            Instant createdAt, Instant updatedAt, long version) {
    static TenantDetails from(Tenant tenant) {
        return new TenantDetails(tenant.getId(), tenant.getName(), tenant.getSlug(), tenant.getStatus(),
                tenant.getCreatedAt(), tenant.getUpdatedAt(), tenant.getVersion());
    }
}
