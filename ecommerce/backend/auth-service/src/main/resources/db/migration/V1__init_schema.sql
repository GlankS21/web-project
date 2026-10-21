-- auth-service schema: credentials, roles and refresh tokens

CREATE TABLE roles
(
    id   BIGSERIAL PRIMARY KEY,
    name VARCHAR(30) NOT NULL,
    CONSTRAINT uk_roles_name UNIQUE (name)
);

CREATE TABLE user_accounts
(
    id            UUID PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    last_login_at TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    version       BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_user_accounts_email UNIQUE (email),
    CONSTRAINT ck_user_accounts_email_lowercase CHECK (email = lower(email))
);

CREATE TABLE user_account_roles
(
    user_account_id UUID   NOT NULL,
    role_id         BIGINT NOT NULL,
    PRIMARY KEY (user_account_id, role_id),
    CONSTRAINT fk_user_account_roles_account FOREIGN KEY (user_account_id)
        REFERENCES user_accounts (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_account_roles_role FOREIGN KEY (role_id)
        REFERENCES roles (id)
);

CREATE TABLE refresh_tokens
(
    id                   UUID PRIMARY KEY,
    user_account_id      UUID        NOT NULL,
    token_hash           VARCHAR(64) NOT NULL,
    expires_at           TIMESTAMPTZ NOT NULL,
    created_at           TIMESTAMPTZ NOT NULL,
    revoked_at           TIMESTAMPTZ,
    replaced_by_token_id UUID,
    CONSTRAINT uk_refresh_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_account FOREIGN KEY (user_account_id)
        REFERENCES user_accounts (id) ON DELETE CASCADE
);

CREATE INDEX idx_refresh_tokens_account ON refresh_tokens (user_account_id);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens (expires_at);
