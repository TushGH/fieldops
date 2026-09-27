package com.fieldops.tenant.application;

import java.util.List;
import java.util.UUID;

import com.fieldops.tenant.domain.MembershipRole;
import com.fieldops.tenant.infrastructure.MembershipRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BusinessDirectory {
    private final MembershipRepository memberships;

    public BusinessDirectory(MembershipRepository memberships) { this.memberships = memberships; }

    /** The caller supplies the authenticated identity, never an ID from request input. */
    public List<Business> forUser(UUID userId) {
        return memberships.findActiveBusinesses(userId).stream()
                .map(row -> new Business(row.getId(), row.getName(), row.getSlug(), row.getRole())).toList();
    }

    public record Business(UUID id, String name, String slug, MembershipRole role) { }
}
