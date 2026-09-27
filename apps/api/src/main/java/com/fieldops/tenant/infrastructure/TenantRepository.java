package com.fieldops.tenant.infrastructure;

import java.util.Optional;
import java.util.UUID;

import com.fieldops.tenant.domain.Tenant;
import org.springframework.data.repository.Repository;

public interface TenantRepository extends Repository<Tenant, UUID> {
    Tenant saveAndFlush(Tenant tenant);
    Optional<Tenant> findById(UUID id);
    Optional<Tenant> findBySlug(String slug);
}
