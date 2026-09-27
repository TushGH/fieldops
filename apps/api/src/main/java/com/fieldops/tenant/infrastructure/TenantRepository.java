package com.fieldops.tenant.infrastructure;

import java.util.Optional;
import java.util.UUID;

import com.fieldops.tenant.domain.Tenant;
import com.fieldops.tenant.domain.TenantStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface TenantRepository extends Repository<Tenant, UUID> {
    Tenant saveAndFlush(Tenant tenant);
    Optional<Tenant> findById(UUID id);
    Optional<Tenant> findBySlug(String slug);
    boolean existsByIdAndStatus(UUID id, TenantStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select tenant from Tenant tenant where tenant.id = :id")
    Optional<Tenant> lockById(UUID id);
}
