package com.fieldops.tenant.application;

import com.fieldops.identity.application.PasswordCredentialService;
import com.fieldops.identity.application.UserService;
import com.fieldops.tenant.domain.MembershipRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates a new business and a new owner together. Never links an existing identity by email. */
@Service
public class BusinessOnboardingService {
    private final TenantService tenants;
    private final UserService users;
    private final PasswordCredentialService credentials;
    private final MembershipService memberships;

    public BusinessOnboardingService(TenantService tenants, UserService users,
                                     PasswordCredentialService credentials, MembershipService memberships) {
        this.tenants = tenants;
        this.users = users;
        this.credentials = credentials;
        this.memberships = memberships;
    }

    @Transactional
    public void onboard(String businessName, String slug, String ownerName, String email, String password) {
        var tenant = tenants.create(businessName, slug);
        var owner = users.create(ownerName, email);
        credentials.provision(owner.id(), password);
        memberships.create(tenant.id(), owner.id(), MembershipRole.BUSINESS_OWNER);
    }
}
