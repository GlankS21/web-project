-- Roles and the initial administrator account.
-- The admin credentials come from Flyway placeholders bound to the ADMIN_EMAIL / ADMIN_PASSWORD
-- environment variables; the password is hashed with bcrypt by pgcrypto ($2a$, cost 10), which is
-- compatible with Spring Security's BCryptPasswordEncoder. Nothing secret is stored in this file.
-- The admin id is shared with user-service (V2__seed_admin_profile.sql).

CREATE EXTENSION IF NOT EXISTS pgcrypto;

INSERT INTO roles (name)
VALUES ('ROLE_CUSTOMER'),
       ('ROLE_ADMIN');

INSERT INTO user_accounts (id, email, password_hash, enabled, created_at, updated_at, version)
VALUES ('00000000-0000-0000-0000-000000000001',
        lower('${admin_email}'),
        crypt('${admin_password}', gen_salt('bf', 10)),
        TRUE,
        now(),
        now(),
        0);

INSERT INTO user_account_roles (user_account_id, role_id)
SELECT '00000000-0000-0000-0000-000000000001', id
FROM roles;
