-- V1: Core identity + RBAC schema (DS §4.2 lines 247-378)

CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    user_id         VARCHAR(50) UNIQUE NOT NULL,
    external_id     VARCHAR(100),
    auth_provider   VARCHAR(20) NOT NULL DEFAULT 'LOCAL',
    full_name       VARCHAR(100) NOT NULL,
    email           VARCHAR(255) UNIQUE NOT NULL,
    department      VARCHAR(50),
    title           VARCHAR(50),
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                      CHECK (status IN ('ACTIVE', 'LOCKED', 'DISABLED')),
    password_hash   VARCHAR(255),
    force_change_pw BOOLEAN NOT NULL DEFAULT FALSE,
    failed_attempts INTEGER NOT NULL DEFAULT 0,
    locked_at       TIMESTAMPTZ,
    valid_from      DATE,
    valid_until     DATE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      BIGINT,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by      BIGINT
);

CREATE INDEX idx_users_user_id ON users(user_id);
CREATE INDEX idx_users_status ON users(status);

CREATE TABLE roles (
    id          BIGSERIAL PRIMARY KEY,
    role_code   VARCHAR(50) UNIQUE NOT NULL,
    role_name   VARCHAR(100) NOT NULL,
    description TEXT,
    is_system   BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE user_roles (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id     BIGINT NOT NULL REFERENCES roles(id),
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    assigned_by BIGINT,
    UNIQUE (user_id, role_id)
);

CREATE TABLE document_categories (
    id                    BIGSERIAL PRIMARY KEY,
    category_code         VARCHAR(20) UNIQUE NOT NULL,
    category_name         VARCHAR(100) NOT NULL,
    description           TEXT,
    review_period_months  INTEGER NOT NULL DEFAULT 24,
    qa_mandatory          BOOLEAN NOT NULL DEFAULT FALSE,
    is_active             BOOLEAN NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE permissions (
    id              BIGSERIAL PRIMARY KEY,
    role_id         BIGINT NOT NULL REFERENCES roles(id),
    category_id     BIGINT NOT NULL REFERENCES document_categories(id),
    department      VARCHAR(50),
    can_view        BOOLEAN NOT NULL DEFAULT FALSE,
    can_download    BOOLEAN NOT NULL DEFAULT FALSE,
    can_create      BOOLEAN NOT NULL DEFAULT FALSE,
    can_edit_draft  BOOLEAN NOT NULL DEFAULT FALSE,
    can_review      BOOLEAN NOT NULL DEFAULT FALSE,
    can_approve     BOOLEAN NOT NULL DEFAULT FALSE,
    can_retire      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (role_id, category_id, department)
);

CREATE INDEX idx_permissions_role ON permissions(role_id);
CREATE INDEX idx_permissions_category ON permissions(category_id);
