#!/usr/bin/env bash
set -euo pipefail

# Postgres init scripts run as superuser before the application connects.
# We pre-create the audit_role principal here so Flyway V2 can grant
# INSERT-only privileges to it. The actual table grants live in V2 SQL.

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
  DO \$\$
  BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'audit_role') THEN
      CREATE ROLE audit_role LOGIN PASSWORD 'audit_dev_password';
    END IF;
  END
  \$\$;
EOSQL
