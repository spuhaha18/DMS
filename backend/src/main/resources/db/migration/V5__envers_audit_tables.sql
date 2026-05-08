-- V5: Hibernate Envers revision tables for @Audited entities (User, Role)
-- Required for ddl-auto: validate with hibernate-envers on the classpath.

-- Hibernate Envers 6.x uses 'revinfo_seq' as the sequence name for REVINFO PK
CREATE SEQUENCE revinfo_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE revinfo (
    rev      INTEGER NOT NULL DEFAULT nextval('revinfo_seq') PRIMARY KEY,
    revtstmp BIGINT
);

CREATE TABLE users_aud (
    id              BIGINT      NOT NULL,
    rev             INTEGER     NOT NULL REFERENCES revinfo(rev),
    revtype         SMALLINT,
    user_id         VARCHAR(50),
    external_id     VARCHAR(100),
    auth_provider   VARCHAR(20),
    full_name       VARCHAR(100),
    email           VARCHAR(255),
    department      VARCHAR(50),
    title           VARCHAR(50),
    status          VARCHAR(20),
    force_change_pw BOOLEAN,
    failed_attempts INTEGER,
    locked_at       TIMESTAMPTZ,
    valid_from      DATE,
    valid_until     DATE,
    created_at      TIMESTAMPTZ,
    updated_at      TIMESTAMPTZ,
    created_by      BIGINT,
    updated_by      BIGINT,
    PRIMARY KEY (id, rev)
);

CREATE TABLE roles_aud (
    id          BIGINT      NOT NULL,
    rev         INTEGER     NOT NULL REFERENCES revinfo(rev),
    revtype     SMALLINT,
    role_code   VARCHAR(50),
    role_name   VARCHAR(100),
    description TEXT,
    is_system   BOOLEAN,
    created_at  TIMESTAMPTZ,
    PRIMARY KEY (id, rev)
);
