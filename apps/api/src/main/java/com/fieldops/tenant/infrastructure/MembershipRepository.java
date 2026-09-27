package com.fieldops.tenant.infrastructure;

import java.util.Optional;
import java.util.UUID;

import com.fieldops.tenant.domain.Membership;
import com.fieldops.tenant.domain.MembershipRole;
import com.fieldops.tenant.domain.MembershipStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.Query;

public interface MembershipRepository extends Repository<Membership, UUID> {
    Membership saveAndFlush(Membership membership);

    // Resolve context as scalar data so a managed actor entity cannot retain
    // stale state before a membership-administration transaction acquires its lock.
    @Query("""
            select membership.id as id, membership.role as role from Membership membership
            where membership.tenantId = :tenantId and membership.userId = :userId and membership.status = :status
            """)
    Optional<AccessSnapshot> findAccess(UUID tenantId, UUID userId, MembershipStatus status);

    interface AccessSnapshot {
        UUID getId();
        MembershipRole getRole();
    }

    Optional<Membership> findByTenantIdAndUserId(UUID tenantId, UUID userId);
    Optional<Membership> findByTenantIdAndId(UUID tenantId, UUID id);
    Page<Membership> findByTenantId(UUID tenantId, Pageable pageable);
    boolean existsByTenantIdAndUserIdAndStatusAndRole(UUID tenantId, UUID userId, MembershipStatus status, MembershipRole role);
    long countByTenantIdAndStatusAndRole(UUID tenantId, MembershipStatus status, MembershipRole role);
}
