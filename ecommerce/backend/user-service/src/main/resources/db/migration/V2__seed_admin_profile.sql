-- Profile of the initial administrator created by auth-service (V2__seed_roles_and_admin.sql).
-- Same fixed id; the email comes from the ADMIN_EMAIL environment variable (Flyway placeholder).

INSERT INTO user_profiles (id, email, first_name, last_name, created_at, updated_at, version)
VALUES ('00000000-0000-0000-0000-000000000001',
        lower('${admin_email}'),
        'System',
        'Administrator',
        now(),
        now(),
        0);
