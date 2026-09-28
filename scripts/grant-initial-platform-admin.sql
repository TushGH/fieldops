-- Operator-only bootstrap. Example: psql -v email=operator@example.com -f this-file.sql
-- The selected database/credentials must be reviewed by the operator first.
\set ON_ERROR_STOP on
BEGIN;
WITH candidate AS (
    SELECT id FROM public.users
    WHERE email = lower(:'email' COLLATE "C") AND status = 'ACTIVE' AND email_verified_at IS NOT NULL
    FOR UPDATE
), granted AS (
    INSERT INTO public.platform_role_grants(id, user_id, role, status, granted_at, version)
    SELECT gen_random_uuid(), id, 'PLATFORM_ADMIN', 'ACTIVE', now(), 0 FROM candidate
    ON CONFLICT (user_id, role) DO NOTHING
    RETURNING id, user_id
)
INSERT INTO public.security_audit_events(id, actor_user_id, action, outcome, target_id, occurred_at, correlation_id)
SELECT gen_random_uuid(), NULL, 'INITIAL_PLATFORM_GRANT', 'SUCCESS', id, now(), gen_random_uuid() FROM granted
RETURNING target_id AS granted_role_id;
COMMIT;
-- Zero returned rows means no eligible identity or a grant already exists.
-- Null actor identifies this controlled initial operator procedure, not a tenant administrator.
