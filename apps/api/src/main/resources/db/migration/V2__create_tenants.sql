CREATE TABLE public.tenants (
    id uuid CONSTRAINT pk_tenants PRIMARY KEY,
    name varchar(200) NOT NULL,
    slug varchar(63) NOT NULL,
    status varchar(20) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT uq_tenants_slug UNIQUE (slug),
    CONSTRAINT ck_tenants_name CHECK (
        name <> '' AND name !~ '[[:cntrl:]]'
        -- Unicode White_Space, matching application input normalization.
        AND name = btrim(name, U&'\0009\000A\000B\000C\000D\0020\0085\00A0\1680\2000\2001\2002\2003\2004\2005\2006\2007\2008\2009\200A\2028\2029\202F\205F\3000')
    ),
    CONSTRAINT ck_tenants_slug CHECK (
        char_length(slug) BETWEEN 3 AND 63 AND slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'
    ),
    CONSTRAINT ck_tenants_status CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    CONSTRAINT ck_tenants_version CHECK (version >= 0)
);
