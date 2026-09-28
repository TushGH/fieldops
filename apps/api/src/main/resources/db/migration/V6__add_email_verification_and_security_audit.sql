ALTER TABLE public.users ADD COLUMN email_verified_at timestamptz;
-- Unknown mailbox ownership stays unknown, including ACTIVE legacy accounts.
CREATE TABLE public.email_challenges (
    id uuid PRIMARY KEY,
    purpose varchar(30) NOT NULL CHECK (purpose IN ('REGISTRATION', 'EMAIL_VERIFICATION')),
    email varchar(254) NOT NULL CHECK (
        email = lower(email COLLATE "C") AND email COLLATE "C" ~ '^[!-~]+@[!-~]+$'
        AND char_length(email) - char_length(replace(email, '@', '')) = 1),
    display_name varchar(200),
    user_id uuid REFERENCES public.users(id) ON DELETE RESTRICT,
    token_hash varchar(64) NOT NULL UNIQUE CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    created_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    consumed_at timestamptz,
    revoked_at timestamptz,
    version bigint NOT NULL CHECK (version >= 0),
    CHECK (expires_at > created_at),
    CHECK (consumed_at IS NULL OR (consumed_at >= created_at AND consumed_at < expires_at)),
    CHECK (revoked_at IS NULL OR revoked_at >= created_at),
    CHECK (consumed_at IS NULL OR revoked_at IS NULL),
    CHECK ((purpose = 'REGISTRATION' AND user_id IS NULL AND display_name IS NOT NULL)
        OR (purpose = 'EMAIL_VERIFICATION' AND user_id IS NOT NULL AND display_name IS NULL))
);
CREATE INDEX idx_email_challenges_email_created ON public.email_challenges(email, created_at);
CREATE TABLE public.security_audit_events (
    id uuid PRIMARY KEY,
    actor_user_id uuid REFERENCES public.users(id) ON DELETE RESTRICT,
    tenant_id uuid REFERENCES public.tenants(id) ON DELETE RESTRICT,
    action varchar(80) NOT NULL,
    outcome varchar(20) NOT NULL CHECK (outcome IN ('SUCCESS', 'FAILURE')),
    target_id uuid,
    occurred_at timestamptz NOT NULL,
    correlation_id uuid NOT NULL
);
-- Tenant audit history, ordered without exposing other businesses' records.
CREATE INDEX idx_security_audit_tenant_time ON public.security_audit_events(tenant_id, occurred_at, id);
