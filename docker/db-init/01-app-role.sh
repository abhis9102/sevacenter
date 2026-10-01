#!/bin/bash
# Runs once, on first DB init (empty volume). Creates the least-privilege runtime role
# that the application connects as. Migrations (Flyway) run as the owner/superuser role;
# this role is subject to Row-Level Security. In production this is done by infra (M6).
set -euo pipefail

APP_PW="${DB_APP_PASSWORD:?DB_APP_PASSWORD must be set for the db-init script}"

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<SQL
DO \$\$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'sevacenter_app') THEN
        CREATE ROLE sevacenter_app LOGIN PASSWORD '${APP_PW}' NOSUPERUSER NOCREATEDB NOCREATEROLE;
    END IF;
END
\$\$;

GRANT CONNECT ON DATABASE ${POSTGRES_DB} TO sevacenter_app;
GRANT USAGE ON SCHEMA public TO sevacenter_app;
-- Table/sequence grants are (re)applied by the V2 Flyway migration once tables exist.
SQL

echo "db-init: role sevacenter_app ensured."
