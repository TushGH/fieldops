CREATE TABLE public.users (
    id uuid CONSTRAINT pk_users PRIMARY KEY,
    display_name varchar(200) NOT NULL,
    email varchar(254) NOT NULL,
    status varchar(20) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_display_name CHECK (
        display_name <> '' AND display_name !~ '[[:cntrl:]]'
        AND display_name = btrim(display_name, U&'\0009\000A\000B\000C\000D\0020\0085\00A0\1680\2000\2001\2002\2003\2004\2005\2006\2007\2008\2009\200A\2028\2029\202F\205F\3000')
    ),
    -- Canonical ASCII storage and a basic shape check; Bean Validation handles
    -- address syntax. Neither check proves mailbox ownership or deliverability.
    CONSTRAINT ck_users_email CHECK (
        email = lower(email COLLATE "C")
        AND email COLLATE "C" ~ '^[!-~]+@[!-~]+$'
        AND char_length(email) - char_length(replace(email, '@', '')) = 1
    ),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT ck_users_version CHECK (version >= 0)
);

CREATE TABLE public.memberships (
    id uuid CONSTRAINT pk_memberships PRIMARY KEY,
    tenant_id uuid NOT NULL,
    user_id uuid NOT NULL,
    status varchar(20) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT fk_memberships_tenant FOREIGN KEY (tenant_id) REFERENCES public.tenants(id) ON DELETE RESTRICT,
    CONSTRAINT fk_memberships_user FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE RESTRICT,
    CONSTRAINT uq_memberships_tenant_user UNIQUE (tenant_id, user_id),
    CONSTRAINT ck_memberships_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_memberships_version CHECK (version >= 0)
);

-- The unique pair index covers tenant-first lookups. This index supports
-- checking referencing memberships when a user is deleted.
CREATE INDEX idx_memberships_user_id ON public.memberships (user_id);
