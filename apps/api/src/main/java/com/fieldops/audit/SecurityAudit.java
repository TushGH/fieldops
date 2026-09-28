package com.fieldops.audit;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Deliberately contains no arbitrary payload, email, password, token, or cookie fields. */
@Service
public class SecurityAudit {
    private final JdbcTemplate jdbc;
    public SecurityAudit(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void record(UUID actor, UUID tenant, String action, String outcome, UUID target) {
        jdbc.update("""
                INSERT INTO security_audit_events(id, actor_user_id, tenant_id, action, outcome, target_id, occurred_at, correlation_id)
                VALUES (?, ?, ?, ?, ?, ?, now(), ?)
                """, UUID.randomUUID(), actor, tenant, action, outcome, target, UUID.randomUUID());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void independent(UUID actor, UUID tenant, String action, String outcome, UUID target) {
        record(actor, tenant, action, outcome, target);
    }
}
