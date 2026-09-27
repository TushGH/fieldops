package com.fieldops.tenant.domain;

import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class MembershipTest {
    @Test
    void keepsRelationshipIdentityAcrossLifecycleChanges() {
        var tenantId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        var membership = Membership.create(tenantId, userId);
        var id = membership.getId();
        assertThat(id.version()).isEqualTo(4);
        assertThat(membership.getStatus()).isEqualTo(MembershipStatus.ACTIVE);
        assertThat(membership.getRole()).isEqualTo(MembershipRole.TECHNICIAN);
        assertThat(membership.getCreatedAt()).isEqualTo(membership.getUpdatedAt());
        membership.deactivate();
        membership.deactivate();
        assertThat(membership.getStatus()).isEqualTo(MembershipStatus.INACTIVE);
        membership.reactivate();
        membership.reactivate();
        assertThat(membership.getStatus()).isEqualTo(MembershipStatus.ACTIVE);
        assertThat(membership.getId()).isEqualTo(id);
        assertThat(membership.getTenantId()).isEqualTo(tenantId);
        assertThat(membership.getUserId()).isEqualTo(userId);
    }

    @Test
    void assignsRolesPerMembershipAndRejectsMissingRoles() {
        var membership = Membership.create(UUID.randomUUID(), UUID.randomUUID(), MembershipRole.BUSINESS_OWNER);
        assertThat(membership.getRole()).isEqualTo(MembershipRole.BUSINESS_OWNER);
        membership.assignRole(MembershipRole.DISPATCHER);
        assertThat(membership.getRole()).isEqualTo(MembershipRole.DISPATCHER);
        assertThatNullPointerException().isThrownBy(() -> membership.assignRole(null));
        assertThatNullPointerException().isThrownBy(() -> Membership.create(UUID.randomUUID(), UUID.randomUUID(), null));
    }

    @Test
    void rejectsMissingRelationshipIdentifiers() {
        assertThatNullPointerException().isThrownBy(() -> Membership.create(null, UUID.randomUUID()));
        assertThatNullPointerException().isThrownBy(() -> Membership.create(UUID.randomUUID(), null));
    }
}
