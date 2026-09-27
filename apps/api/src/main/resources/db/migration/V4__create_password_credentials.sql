CREATE TABLE public.password_credentials (
    user_id uuid CONSTRAINT pk_password_credentials PRIMARY KEY,
    password_hash varchar(255) NOT NULL,
    created_at timestamptz NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT fk_password_credentials_user FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE RESTRICT,
    CONSTRAINT ck_password_credentials_hash CHECK (password_hash LIKE '{bcrypt}$2%' AND char_length(password_hash) = 68),
    CONSTRAINT ck_password_credentials_version CHECK (version >= 0)
);
