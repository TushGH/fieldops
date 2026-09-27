package com.fieldops.tenant.infrastructure;

import java.util.Optional;
import java.util.UUID;

import com.fieldops.tenant.domain.Membership;
import org.springframework.data.repository.Repository;

public interface MembershipRepository extends Repository<Membership, UUID> {
    Membership saveAndFlush(Membership membership);
    Optional<Membership> findByTenantIdAndUserId(UUID tenantId, UUID userId);
}
