package com.fieldops.tenant.domain;

/** Roles belong to one business membership, never to the global User. */
public enum MembershipRole {
    BUSINESS_OWNER,
    DISPATCHER,
    TECHNICIAN
}
