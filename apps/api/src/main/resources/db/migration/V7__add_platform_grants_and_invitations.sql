ALTER TABLE public.tenants DROP CONSTRAINT ck_tenants_status;
ALTER TABLE public.tenants ADD CONSTRAINT ck_tenants_status
    CHECK (status IN ('PROVISIONING', 'ACTIVE', 'SUSPENDED'));
CREATE TABLE public.platform_role_grants (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES public.users(id) ON DELETE RESTRICT,
    role varchar(30) NOT NULL CHECK (role = 'PLATFORM_ADMIN'),
    status varchar(20) NOT NULL CHECK (status IN ('ACTIVE', 'REVOKED')),
    granted_by_user_id uuid REFERENCES public.users(id) ON DELETE RESTRICT,
    granted_at timestamptz NOT NULL,
    revoked_by_user_id uuid REFERENCES public.users(id) ON DELETE RESTRICT,
    revoked_at timestamptz,
    version bigint NOT NULL CHECK (version >= 0),
    UNIQUE(user_id, role),
    CHECK ((status = 'ACTIVE' AND revoked_at IS NULL AND revoked_by_user_id IS NULL)
        OR (status = 'REVOKED' AND revoked_at IS NOT NULL AND revoked_by_user_id IS NOT NULL)),
    CHECK (revoked_at IS NULL OR revoked_at >= granted_at)
);
-- A null grantor is reserved for the audited initial operator bootstrap.
CREATE TABLE public.invitations (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES public.tenants(id) ON DELETE RESTRICT,
    email varchar(254) NOT NULL CHECK (
        email = lower(email COLLATE "C") AND email COLLATE "C" ~ '^[!-~]+@[!-~]+$'
        AND char_length(email) - char_length(replace(email, '@', '')) = 1),
    role varchar(30) NOT NULL CHECK (role IN ('BUSINESS_OWNER', 'DISPATCHER', 'TECHNICIAN')),
    purpose varchar(20) NOT NULL CHECK (purpose IN ('TEAM_MEMBER', 'INITIAL_OWNER')),
    status varchar(20) NOT NULL CHECK (status IN ('PENDING', 'ACCEPTED', 'REVOKED', 'EXPIRED')),
    token_hash varchar(64) NOT NULL UNIQUE CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    invited_by_user_id uuid NOT NULL REFERENCES public.users(id) ON DELETE RESTRICT,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    accepted_at timestamptz,
    accepted_by_user_id uuid REFERENCES public.users(id) ON DELETE RESTRICT,
    revoked_at timestamptz,
    revoked_by_user_id uuid REFERENCES public.users(id) ON DELETE RESTRICT,
    version bigint NOT NULL CHECK (version >= 0),
    CHECK (purpose <> 'INITIAL_OWNER' OR role = 'BUSINESS_OWNER'),
    CHECK (expires_at > created_at AND updated_at >= created_at),
    CHECK ((status = 'ACCEPTED' AND accepted_at IS NOT NULL AND accepted_by_user_id IS NOT NULL
            AND accepted_at >= created_at AND accepted_at < expires_at)
        OR (status <> 'ACCEPTED' AND accepted_at IS NULL AND accepted_by_user_id IS NULL)),
    CHECK ((status = 'REVOKED' AND revoked_at IS NOT NULL AND revoked_by_user_id IS NOT NULL AND revoked_at >= created_at)
        OR (status <> 'REVOKED' AND revoked_at IS NULL AND revoked_by_user_id IS NULL))
);
CREATE UNIQUE INDEX uq_invitations_pending_recipient ON public.invitations(tenant_id, email) WHERE status = 'PENDING';
CREATE INDEX idx_invitations_tenant_created ON public.invitations(tenant_id, created_at, id);
CREATE INDEX idx_invitations_recipient ON public.invitations(email, status, expires_at);
