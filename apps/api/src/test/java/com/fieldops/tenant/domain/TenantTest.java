package com.fieldops.tenant.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class TenantTest {
    @Test
    void provisioningCannotBeActivatedByOrdinaryReactivation() {
        var tenant = Tenant.provision("Invited business", "invited-business");
        assertThat(tenant.getStatus()).isEqualTo(TenantStatus.PROVISIONING);
        assertThatThrownBy(tenant::reactivate).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void normalizesInputAndKeepsIdentityStableThroughLifecycle() {
        var tenant = Tenant.create("\u00a0 ABC Heating \u2003", " ABC-Heating ");
        var id = tenant.getId();
        var createdAt = tenant.getCreatedAt();
        assertThat(id.version()).isEqualTo(4);
        assertThat(tenant.getName()).isEqualTo("ABC Heating");
        assertThat(tenant.getSlug()).isEqualTo("abc-heating");
        assertThat(tenant.getStatus()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(tenant.getUpdatedAt()).isEqualTo(createdAt);
        tenant.suspend();
        tenant.suspend();
        assertThat(tenant.getStatus()).isEqualTo(TenantStatus.SUSPENDED);
        tenant.rename("New Name");
        tenant.reactivate();
        tenant.reactivate();
        assertThat(tenant.getStatus()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(tenant.getName()).isEqualTo("New Name");
        assertThat(tenant.getSlug()).isEqualTo("abc-heating");
        assertThat(tenant.getId()).isEqualTo(id);
        assertThat(tenant.getCreatedAt()).isEqualTo(createdAt);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\u00a0\u2003", "Bad\nName", "Bad\u0000Name"})
    void rejectsInvalidNames(String name) {
        assertThatIllegalArgumentException().isThrownBy(() -> Tenant.create(name, "valid-slug"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"ab", "-abc", "abc-", "abc--def", "abc_def", "abc def", "café"})
    void rejectsInvalidSlugs(String slug) {
        assertThatIllegalArgumentException().isThrownBy(() -> Tenant.create("Valid Name", slug));
    }

    @Test
    void validatesLengthBoundariesAndRename() {
        var tenant = Tenant.create("名".repeat(200), "a".repeat(63));
        assertThat(tenant.getName()).hasSize(200);
        assertThatIllegalArgumentException().isThrownBy(() -> Tenant.create("x".repeat(201), "abc"));
        assertThatIllegalArgumentException().isThrownBy(() -> Tenant.create("Name", "a".repeat(64)));
        assertThatIllegalArgumentException().isThrownBy(() -> tenant.rename(" "));
        assertThat(tenant.getName()).isEqualTo("名".repeat(200));
    }
}
