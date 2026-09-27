package com.fieldops.tenant.application;

import java.util.List;
import java.util.UUID;

import com.fieldops.tenant.domain.Membership;
import com.fieldops.tenant.domain.MembershipRole;
import com.fieldops.tenant.domain.MembershipStatus;
import com.fieldops.tenant.domain.TenantStatus;
import com.fieldops.tenant.infrastructure.AuthenticatedTenantContext;
import com.fieldops.tenant.infrastructure.AuthenticatedTenantContext.Selection;
import com.fieldops.tenant.infrastructure.MembershipRepository;
import com.fieldops.tenant.infrastructure.TenantRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Request-facing tenant operations. No method accepts a caller-chosen ownership ID. */
@Service
@Transactional(readOnly = true)
public class TenantWorkspaceService {
    private final AuthenticatedTenantContext context;
    private final TenantRepository tenants;
    private final MembershipRepository memberships;

    public TenantWorkspaceService(AuthenticatedTenantContext context, TenantRepository tenants, MembershipRepository memberships) {
        this.context = context;
        this.tenants = tenants;
        this.memberships = memberships;
    }

    public Selection currentContext() {
        return context.require();
    }

    public TenantDetails currentTenant() {
        return TenantDetails.from(tenants.findById(context.require().tenantId()).orElseThrow(TenantAccessException::notFound));
    }

    @Transactional
    public TenantDetails rename(String name) {
        var selection = context.require();
        requireOwner(selection);
        var tenant = tenants.lockById(selection.tenantId()).orElseThrow(TenantAccessException::notFound);
        requireOwner(selection);
        tenant.rename(name);
        return TenantDetails.from(tenants.saveAndFlush(tenant));
    }

    public MembershipPage listMemberships(int page, int size) {
        var selection = context.require();
        requireOwner(selection);
        if (page < 0 || size < 1 || size > 100) {
            throw new TenantAccessException(400, "INVALID_PAGE", "Page must be nonnegative and size between 1 and 100.");
        }
        var result = memberships.findByTenantId(selection.tenantId(), PageRequest.of(page, size, Sort.by("id")));
        return new MembershipPage(result.getContent().stream().map(MembershipDetails::from).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements());
    }

    public MembershipDetails membership(UUID id) {
        var selection = context.require();
        requireOwner(selection);
        return MembershipDetails.from(findMembership(selection.tenantId(), id));
    }

    @Transactional
    public MembershipDetails changeRole(UUID id, MembershipRole role) {
        var selection = lockOwnerOperation();
        var membership = findMembership(selection.tenantId(), id);
        if (role != MembershipRole.BUSINESS_OWNER) preserveLastOwner(membership);
        membership.assignRole(role);
        return MembershipDetails.from(memberships.saveAndFlush(membership));
    }

    @Transactional
    public MembershipDetails deactivate(UUID id) {
        var selection = lockOwnerOperation();
        var membership = findMembership(selection.tenantId(), id);
        preserveLastOwner(membership);
        membership.deactivate();
        return MembershipDetails.from(memberships.saveAndFlush(membership));
    }

    @Transactional
    public MembershipDetails reactivate(UUID id) {
        var selection = lockOwnerOperation();
        var membership = findMembership(selection.tenantId(), id);
        membership.reactivate();
        return MembershipDetails.from(memberships.saveAndFlush(membership));
    }

    private Selection lockOwnerOperation() {
        var selection = context.require();
        requireOwner(selection);
        // Serialize membership administration per tenant so two owners cannot
        // concurrently remove/demote each other and leave no active owner.
        tenants.lockById(selection.tenantId()).orElseThrow(TenantAccessException::notFound);
        // Aggregate queries read current database state, not a cached JPA entity
        // or a role captured before waiting for this lock.
        requireOwner(selection);
        return selection;
    }

    private void requireOwner(Selection selection) {
        if (!tenants.existsByIdAndStatus(selection.tenantId(), TenantStatus.ACTIVE)
                || !memberships.existsByTenantIdAndUserIdAndStatusAndRole(selection.tenantId(), selection.userId(),
                        MembershipStatus.ACTIVE, MembershipRole.BUSINESS_OWNER)) {
            throw TenantAccessException.forbidden();
        }
    }

    private Membership findMembership(UUID tenantId, UUID id) {
        return memberships.findByTenantIdAndId(tenantId, id).orElseThrow(TenantAccessException::notFound);
    }

    private void preserveLastOwner(Membership membership) {
        if (membership.getStatus() == MembershipStatus.ACTIVE && membership.getRole() == MembershipRole.BUSINESS_OWNER
                && memberships.countByTenantIdAndStatusAndRole(membership.getTenantId(), MembershipStatus.ACTIVE,
                        MembershipRole.BUSINESS_OWNER) <= 1) {
            throw new TenantAccessException(409, "LAST_OWNER", "The tenant must retain an active business owner.");
        }
    }

    public record MembershipPage(List<MembershipDetails> items, int page, int size, long totalElements) { }
}
