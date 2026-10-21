-- user-service schema: profiles and address book

CREATE TABLE user_profiles
(
    id            UUID PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    first_name    VARCHAR(100),
    last_name     VARCHAR(100),
    phone_number  VARCHAR(20),
    date_of_birth DATE,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    version       BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_user_profiles_email ON user_profiles (lower(email));

CREATE TABLE addresses
(
    id             UUID PRIMARY KEY,
    user_id        UUID         NOT NULL,
    recipient_name VARCHAR(150) NOT NULL,
    phone_number   VARCHAR(20)  NOT NULL,
    address_line1  VARCHAR(255) NOT NULL,
    address_line2  VARCHAR(255),
    ward           VARCHAR(100),
    district       VARCHAR(100),
    city           VARCHAR(100) NOT NULL,
    postal_code    VARCHAR(20),
    country_code   VARCHAR(2)   NOT NULL,
    is_default     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,
    version        BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT fk_addresses_user FOREIGN KEY (user_id) REFERENCES user_profiles (id) ON DELETE CASCADE
);

CREATE INDEX idx_addresses_user ON addresses (user_id);

-- At most one default address per user
CREATE UNIQUE INDEX uk_addresses_one_default_per_user ON addresses (user_id) WHERE is_default;
