-- Test-only: mirrors docker/db-init/01-app-role.sh. Runs as the container superuser before
-- Flyway, so V2's grants reach the least-privilege role that RLS applies to.
-- The password is a fixed test value for a throwaway container, not a secret.
CREATE ROLE sevacenter_app LOGIN PASSWORD 'app-role-test-only' NOSUPERUSER NOCREATEDB NOCREATEROLE;
GRANT CONNECT ON DATABASE test TO sevacenter_app;
GRANT USAGE ON SCHEMA public TO sevacenter_app;
