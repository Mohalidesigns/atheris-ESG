#!/bin/bash
# First start of the pilot database (docker-entrypoint-initdb.d). esg_owner owns the schema and runs migrations;
# esg_app is the application login without superuser or BYPASSRLS, so Row-Level Security applies to it (WP-11).
set -euo pipefail
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<SQL
CREATE ROLE esg_owner LOGIN PASSWORD '${ESG_OWNER_PASSWORD}';
CREATE ROLE esg_app LOGIN NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE PASSWORD '${ESG_APP_PASSWORD}';
CREATE DATABASE esg OWNER esg_owner;
GRANT CONNECT ON DATABASE esg TO esg_app;
SQL
