package com.fieldops.tenant.infrastructure;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

import com.fieldops.tenant.domain.Membership;
import com.fieldops.tenant.domain.MembershipRole;
import com.fieldops.tenant.domain.MembershipStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.Query;

public interface MembershipRepository extends Repository<Membership, UUID> {
    @Query("""
            select tenant.id as id, tenant.name as name, tenant.slug as slug, membership.role as role
            from Membership membership join Tenant tenant on tenant.id = membership.tenantId
            where membership.userId = :userId
              and membership.status = com.fieldops.tenant.domain.MembershipStatus.ACTIVE
              and tenant.status = com.fieldops.tenant.domain.TenantStatus.ACTIVE
            order by tenant.name, tenant.id
            """)
    List<BusinessSummary> findActiveBusinesses(UUID userId);

    interface BusinessSummary {
        UUID getId();
        String getName();
        String getSlug();
        MembershipRole getRole();
    }

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
