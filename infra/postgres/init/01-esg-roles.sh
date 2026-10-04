#!/bin/bash
# Runs once on first container start (docker-entrypoint-initdb.d).
# esg_owner owns the database and runs Flyway migrations.
# esg_app is the application login: not the owner, no superuser, no BYPASSRLS, so RLS applies (WP-11).
set -euo pipefail
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<SQL
CREATE ROLE esg_owner LOGIN PASSWORD '${ESG_OWNER_PASSWORD}';
CREATE ROLE esg_app LOGIN NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE PASSWORD '${ESG_APP_PASSWORD}';
CREATE DATABASE esg_dev OWNER esg_owner;
GRANT CONNECT ON DATABASE esg_dev TO esg_app;
SQL
