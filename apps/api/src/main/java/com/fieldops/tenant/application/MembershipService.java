package com.fieldops.tenant.application;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.fieldops.tenant.domain.Membership;
import com.fieldops.tenant.infrastructure.MembershipRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Internal relationship persistence. A membership record is not an access grant. */
@Service
@Transactional(readOnly = true)
public class MembershipService {
    private final MembershipRepository memberships;

    public MembershipService(MembershipRepository memberships) {
        this.memberships = memberships;
    }

    @Transactional
    public MembershipDetails create(UUID tenantId, UUID userId) {
        // Foreign keys and pair uniqueness validate relationships atomically,
        // including races with deletion or another membership creation.
        return MembershipDetails.from(memberships.saveAndFlush(Membership.create(tenantId, userId)));
    }

    public Optional<MembershipDetails> find(UUID tenantId, UUID userId) {
        return memberships.findByTenantIdAndUserId(
                Objects.requireNonNull(tenantId, "Tenant ID is required"),
                Objects.requireNonNull(userId, "User ID is required")).map(MembershipDetails::from);
    }
}
