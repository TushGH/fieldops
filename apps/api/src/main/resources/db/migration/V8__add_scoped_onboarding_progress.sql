CREATE TABLE public.business_onboarding (
    tenant_id uuid PRIMARY KEY REFERENCES public.tenants(id) ON DELETE RESTRICT,
    completed_version integer NOT NULL CHECK (completed_version >= 0),
    completed_at timestamptz,
    version bigint NOT NULL CHECK (version >= 0),
    CHECK ((completed_version = 0 AND completed_at IS NULL) OR (completed_version > 0 AND completed_at IS NOT NULL))
);
CREATE TABLE public.membership_onboarding (
    membership_id uuid NOT NULL REFERENCES public.memberships(id) ON DELETE RESTRICT,
    flow_key varchar(50) NOT NULL CHECK (flow_key IN ('DISPATCHER_SETUP', 'TECHNICIAN_SETUP')),
    completed_version integer NOT NULL CHECK (completed_version >= 0),
    completed_at timestamptz,
    version bigint NOT NULL CHECK (version >= 0),
    PRIMARY KEY (membership_id, flow_key),
    CHECK ((completed_version = 0 AND completed_at IS NULL) OR (completed_version > 0 AND completed_at IS NOT NULL))
);
-- A missing or zero progress record means incomplete; never fabricate completion.
INSERT INTO public.business_onboarding(tenant_id, completed_version, completed_at, version)
SELECT id, 0, NULL, 0 FROM public.tenants;
