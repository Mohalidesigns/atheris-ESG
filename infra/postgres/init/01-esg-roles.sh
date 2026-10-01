#!/bin/bash
# Runs once on first container start (docker-entrypoint-initdb.d).
# esg_owner owns the database and runs Flyway migrations.
# WP-11 adds esg_app: a non-owner login role so Row-Level Security applies to the application.
set -euo pipefail
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<SQL
CREATE ROLE esg_owner LOGIN PASSWORD '${ESG_OWNER_PASSWORD}';
CREATE DATABASE esg_dev OWNER esg_owner;
SQL
