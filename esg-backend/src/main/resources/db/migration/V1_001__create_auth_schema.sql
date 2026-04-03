-- ============================================================
-- ESG Pro Platform - Auth Schema
-- TRD Section 4.2: Core Shared Schema
-- ============================================================

-- Tenants & Organisations
CREATE TABLE tenants (
    id              VARCHAR(36) PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    slug            VARCHAR(100) UNIQUE NOT NULL,
    subscription_tier VARCHAR(50) NOT NULL DEFAULT 'starter',
    country_code    VARCHAR(3) NOT NULL,
    industry_sector VARCHAR(100),
    billing_currency VARCHAR(3) NOT NULL DEFAULT 'NGN',
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    max_users       INT NOT NULL DEFAULT 10,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE organisations (
    id              VARCHAR(36) PRIMARY KEY,
    tenant_id       VARCHAR(36) NOT NULL REFERENCES tenants(id),
    parent_org_id   VARCHAR(36) REFERENCES organisations(id),
    name            VARCHAR(255) NOT NULL,
    org_type        VARCHAR(50),
    country_code    VARCHAR(3),
    state_region    VARCHAR(100),
    address         TEXT,
    latitude        DECIMAL(10,7),
    longitude       DECIMAL(10,7),
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Users & Authentication
CREATE TABLE users (
    id              VARCHAR(36) PRIMARY KEY,
    tenant_id       VARCHAR(36) NOT NULL REFERENCES tenants(id),
    email           VARCHAR(255) UNIQUE NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    first_name      VARCHAR(100) NOT NULL,
    last_name       VARCHAR(100) NOT NULL,
    phone           VARCHAR(20),
    mfa_enabled     BOOLEAN NOT NULL DEFAULT FALSE,
    mfa_secret      VARCHAR(255),
    preferred_language VARCHAR(5) DEFAULT 'en',
    timezone        VARCHAR(50) DEFAULT 'Africa/Lagos',
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    last_login_at   TIMESTAMP,
    password_changed_at TIMESTAMP,
    failed_login_count INT DEFAULT 0,
    locked_until    TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- RBAC Tables
CREATE TABLE roles (
    id              VARCHAR(36) PRIMARY KEY,
    tenant_id       VARCHAR(36) NOT NULL REFERENCES tenants(id),
    name            VARCHAR(100) NOT NULL,
    description     TEXT,
    is_system_role  BOOLEAN DEFAULT FALSE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(tenant_id, name)
);

CREATE TABLE permissions (
    id              VARCHAR(36) PRIMARY KEY,
    resource        VARCHAR(100) NOT NULL,
    action          VARCHAR(50) NOT NULL,
    description     TEXT,
    UNIQUE(resource, action)
);

CREATE TABLE role_permissions (
    role_id         VARCHAR(36) REFERENCES roles(id) ON DELETE CASCADE,
    permission_id   VARCHAR(36) REFERENCES permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE user_roles (
    id              VARCHAR(36) PRIMARY KEY,
    user_id         VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id         VARCHAR(36) NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    org_scope_id    VARCHAR(36) REFERENCES organisations(id),
    granted_by      VARCHAR(36) REFERENCES users(id),
    granted_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at      TIMESTAMP
);

CREATE TABLE user_sessions (
    id              VARCHAR(36) PRIMARY KEY,
    user_id         VARCHAR(36) NOT NULL REFERENCES users(id),
    refresh_token_hash VARCHAR(255) NOT NULL,
    device_info     TEXT,
    ip_address      VARCHAR(45),
    expires_at      TIMESTAMP NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes
CREATE INDEX idx_users_tenant ON users(tenant_id);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_organisations_tenant ON organisations(tenant_id);
CREATE INDEX idx_roles_tenant ON roles(tenant_id);
CREATE INDEX idx_user_roles_user ON user_roles(user_id);
CREATE INDEX idx_user_sessions_user ON user_sessions(user_id);
