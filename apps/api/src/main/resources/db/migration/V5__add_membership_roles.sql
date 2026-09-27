-- No existing membership is automatically promoted to business owner.
ALTER TABLE public.memberships ADD COLUMN role varchar(30) NOT NULL DEFAULT 'TECHNICIAN';
ALTER TABLE public.memberships ALTER COLUMN role DROP DEFAULT;
ALTER TABLE public.memberships ADD CONSTRAINT ck_memberships_role
    CHECK (role IN ('BUSINESS_OWNER', 'DISPATCHER', 'TECHNICIAN'));

-- Supports stable tenant-scoped membership pagination; the existing pair
-- index remains responsible for membership lookup and uniqueness.
CREATE INDEX idx_memberships_tenant_id_id ON public.memberships (tenant_id, id);
