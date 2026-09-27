package com.fieldops.tenant.application;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.fieldops.tenant.domain.Tenant;
import com.fieldops.tenant.infrastructure.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Internal persistence use cases; callers must add authorization before exposing them externally. */
@Service
@Transactional(readOnly = true)
public class TenantService {
    private final TenantRepository tenants;

    public TenantService(TenantRepository tenants) {
        this.tenants = tenants;
    }

    @Transactional
    public TenantDetails create(String name, String slug) {
        // The database unique constraint arbitrates concurrent slug claims.
        return TenantDetails.from(tenants.saveAndFlush(Tenant.create(name, slug)));
    }

    public Optional<TenantDetails> findById(UUID id) {
        return tenants.findById(Objects.requireNonNull(id, "Tenant ID is required"))
                .map(TenantDetails::from);
    }
}
